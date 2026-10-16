const LOCATION_STORAGE_KEY = "workerCurrentLocation";
const LOCATION_CACHE_TTL = 30 * 60 * 1000;

function normalizeLocation(location) {
  const latitude = Number(location?.latitude);
  const longitude = Number(location?.longitude);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
  if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) return null;
  if (longitude === 0 && latitude === 0) return null;
  const displayName = location.address || location.name || location.title;
  return {
    latitude,
    longitude,
    updatedAt: Date.now(),
    ...(displayName ? { name: String(displayName) } : {}),
  };
}

export function getCachedLocation() {
  const cached = uni.getStorageSync(LOCATION_STORAGE_KEY);
  if (!cached || !Number.isFinite(Number(cached.latitude)) || !Number.isFinite(Number(cached.longitude)) || !cached.updatedAt) return null;
  if (Date.now() - Number(cached.updatedAt) > LOCATION_CACHE_TTL) return null;
  return cached;
}

export function requestCurrentLocation({ force = false } = {}) {
  if (!force) {
    const cached = getCachedLocation();
    if (cached) return Promise.resolve(cached);
  }

  return new Promise((resolve) => {
    const getLocation = () => {
      uni.getLocation({
        type: "gcj02",
        success: (location) => {
          const normalized = normalizeLocation(location);
          if (normalized) uni.setStorageSync(LOCATION_STORAGE_KEY, normalized);
          resolve(normalized);
        },
        fail: () => resolve(null),
      });
    };

    // 微信新版本在调用 getLocation 前可能要求先完成隐私授权。
    // 未提供该 API 的端（H5/App）直接走 uni.getLocation。
    if (typeof wx === "undefined" || typeof wx.getPrivacySetting !== "function") {
      getLocation();
      return;
    }
    wx.getPrivacySetting({
      success: ({ needAuthorization }) => {
        if (!needAuthorization) {
          getLocation();
          return;
        }
        if (typeof wx.requirePrivacyAuthorize !== "function") {
          resolve(null);
          return;
        }
        wx.requirePrivacyAuthorize({
          success: getLocation,
          fail: () => resolve(null),
        });
      },
      fail: getLocation,
    });
  });
}
