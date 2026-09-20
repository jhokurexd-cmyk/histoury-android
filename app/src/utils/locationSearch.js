/**
 * Location search, from two sources.
 *
 * Historical sites and commercial places are searched differently on
 * purpose. OpenStreetMap knows Intramuros' landmarks well — forts, churches
 * and bastions are exactly what volunteer mappers record — and it is free,
 * needs no key, and places no restrictions on storing what it returns.
 * Google knows businesses, which OpenStreetMap often misses entirely: a café
 * that opened last year is in one and not the other.
 *
 * So sites use OpenStreetMap and places use Google. Neither is better
 * overall; they are good at different things.
 */

/** Roughly the centre of Intramuros, used to bias every search. */
export const INTRAMUROS = { lat: 14.5896, lng: 120.9757 };

// ---------------------------------------------------------------- OSM

/**
 * Photon, the OpenStreetMap geocoder built for type-ahead.
 *
 * Nominatim is the better-known OSM search, but it is designed for whole
 * queries rather than partial ones and asks callers to stay under one
 * request per second — which a search box cannot honour. Photon indexes the
 * same data and is built for exactly this.
 */
export async function searchOpenStreetMap(query, signal) {

  if (query.trim().length < 3) return [];

  const url = new URL("https://photon.komoot.io/api/");
  url.searchParams.set("q", query);
  url.searchParams.set("limit", "6");
  // Biased, not restricted: results near Intramuros float to the top, but a
  // site just outside the walls is still findable.
  url.searchParams.set("lat", String(INTRAMUROS.lat));
  url.searchParams.set("lon", String(INTRAMUROS.lng));

  const response = await fetch(url, { signal });
  if (!response.ok) throw new Error(`Search failed (${response.status}).`);

  const data = await response.json();

  return (data.features ?? []).map((feature) => {
    const p = feature.properties ?? {};
    const [lng, lat] = feature.geometry?.coordinates ?? [];

    // Photon returns address parts rather than one formatted line, so the
    // readable address is assembled here.
    const address = [p.street, p.district, p.city, p.state, p.country]
      .filter(Boolean)
      .join(", ");

    return {
      id: `${p.osm_type}${p.osm_id}`,
      name: p.name || p.street || "Unnamed location",
      address,
      lat,
      lng,
      source: "osm",
    };
  });
}

// ---------------------------------------------------------------- Google

let googleLoader = null;

/**
 * Loads the Maps JavaScript API once, on demand.
 *
 * Deliberately not loaded at app start: the admin panel's maps are Leaflet,
 * so most sessions never need Google at all. Loading it lazily keeps that
 * script — and its cost — out of every page.
 *
 * Note the script waits for `google.maps.places` rather than calling
 * `google.maps.importLibrary`. That function only exists when the API is
 * loaded through Google's inline bootstrap snippet; with a plain script tag
 * it is undefined, and calling it fails with "importLibrary is not a
 * function" even though the API loaded correctly. Requesting the places
 * library in the URL puts the classes on `google.maps.places` directly,
 * which is what this uses.
 */
function loadGoogleMaps() {

  if (window.google?.maps?.places) return Promise.resolve();
  if (googleLoader) return googleLoader;

  const key = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
  if (!key) {
    return Promise.reject(
      new Error("VITE_GOOGLE_MAPS_API_KEY is not set in .env"),
    );
  }

  googleLoader = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src =
      `https://maps.googleapis.com/maps/api/js?key=${key}` +
      "&libraries=places&v=weekly";
    script.async = true;
    script.defer = true;
    script.onload = () => {
      if (window.google?.maps?.places) {
        resolve();
      } else {
        googleLoader = null;
        reject(new Error("Google Maps loaded without the Places library."));
      }
    };
    script.onerror = () => {
      // Cleared so a later attempt can retry rather than reusing a promise
      // that will never resolve.
      googleLoader = null;
      reject(new Error("Could not load Google Maps. Check the API key."));
    };
    document.head.appendChild(script);
  });

  return googleLoader;
}

let sessionToken = null;

/**
 * Google Places autocomplete.
 *
 * A session token groups the keystrokes of one search with the final
 * selection into a single billable unit. Without it every character typed is
 * charged separately, which is the difference between a few requests a day
 * and a few hundred.
 */
export async function searchGooglePlaces(query, signal) {

  if (query.trim().length < 3) return [];

  await loadGoogleMaps();

  const places = window.google.maps.places;

  // Places API (New). If this class is missing, the project is serving the
  // legacy Places library — which means Places API (New) is not enabled on
  // the key, and saying so is more useful than a TypeError.
  if (!places?.AutocompleteSuggestion) {
    throw new Error(
      "Places API (New) is not enabled for this key. Enable it in the " +
        "Google Cloud console.",
    );
  }

  if (!sessionToken) sessionToken = new places.AutocompleteSessionToken();

  const { AutocompleteSuggestion } = places;

  const { suggestions } = await AutocompleteSuggestion.fetchAutocompleteSuggestions({
    input: query,
    sessionToken,
    locationBias: {
      center: INTRAMUROS,
      radius: 3000,
    },
  });

  if (signal?.aborted) return [];

  return (suggestions ?? [])
    .map((item) => item.placePrediction)
    .filter(Boolean)
    .map((prediction) => ({
      id: prediction.placeId,
      name: prediction.mainText?.text ?? prediction.text?.text ?? "",
      address: prediction.secondaryText?.text ?? "",
      // Coordinates are not in the prediction — they cost a second call, made
      // only for the one result the admin actually picks.
      lat: null,
      lng: null,
      source: "google",
      prediction,
    }));
}

/**
 * Fetches the coordinates for a chosen Google suggestion.
 *
 * Separate from the search because Places bills for details, and an admin
 * types through a dozen predictions to choose one. Resolving only the
 * selection is the difference between one detail call and twelve.
 */
export async function resolveGooglePlace(result) {

  if (result.source !== "google" || !result.prediction) return result;

  const place = result.prediction.toPlace();
  await place.fetchFields({ fields: ["location", "formattedAddress", "displayName"] });

  // The session ends with the selection; the next search starts a new one.
  sessionToken = null;

  return {
    ...result,
    name: place.displayName ?? result.name,
    address: place.formattedAddress ?? result.address,
    lat: place.location?.lat(),
    lng: place.location?.lng(),
  };
}

/** Resolves a result from either source to one with coordinates. */
export async function resolveResult(result) {
  return result.source === "google" ? resolveGooglePlace(result) : result;
}
