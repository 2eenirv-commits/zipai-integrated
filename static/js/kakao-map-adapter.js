(function (global) {
  'use strict';

  const MIN_ZOOM = 7;
  const MAX_ZOOM = 19;

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, Number(value)));
  }

  function toKakaoLevel(zoom) {
    return 20 - clamp(Math.round(zoom), MIN_ZOOM, MAX_ZOOM);
  }

  function fromKakaoLevel(level) {
    return 20 - Number(level);
  }

  function latLng(value) {
    if (value && typeof value.getLat === 'function' && typeof value.getLng === 'function') return value;
    if (Array.isArray(value)) return new kakao.maps.LatLng(Number(value[0]), Number(value[1]));
    if (value && typeof value.lat === 'function' && typeof value.lng === 'function') {
      return new kakao.maps.LatLng(Number(value.lat()), Number(value.lng()));
    }
    return new kakao.maps.LatLng(Number(value.latitude), Number(value.longitude));
  }

  function coordinates(value) {
    const point = latLng(value);
    return { latitude: point.getLat(), longitude: point.getLng() };
  }

  function boundsFrom(points) {
    const list = points.map(latLng);
    const nativeBounds = new kakao.maps.LatLngBounds();
    list.forEach(function (point) { nativeBounds.extend(point); });
    const latitudes = list.map(function (point) { return point.getLat(); });
    const longitudes = list.map(function (point) { return point.getLng(); });
    const limits = {
      minLat: Math.min.apply(null, latitudes), maxLat: Math.max.apply(null, latitudes),
      minLng: Math.min.apply(null, longitudes), maxLng: Math.max.apply(null, longitudes)
    };
    return {
      native: nativeBounds,
      contains: function (point) {
        const value = coordinates(point);
        return value.latitude >= limits.minLat && value.latitude <= limits.maxLat
          && value.longitude >= limits.minLng && value.longitude <= limits.maxLng;
      }
    };
  }

  function htmlIcon(options) {
    return { options: options || {} };
  }

  function marker(position, options) {
    options = options || {};
    const content = document.createElement('div');
    content.style.position = 'relative';
    content.style.width = '0';
    content.style.height = '0';
    let currentMap = null;
    let clickHandler = null;
    let clickListener = null;

    function applyIcon(icon) {
      const iconOptions = icon && icon.options ? icon.options : {};
      content.className = 'kakao-html-marker-host' + (iconOptions.className ? ' ' + iconOptions.className : '');
      content.innerHTML = iconOptions.html || '';
      content.title = options.title || '';
    }

    applyIcon(options.icon);
    const nativeMarker = new kakao.maps.CustomOverlay({
      position: latLng(position),
      content: content,
      xAnchor: 0,
      yAnchor: 0,
      zIndex: 1
    });

    const wrapper = {
      _native: nativeMarker,
      _map: null,
      addTo: function (map) {
        currentMap = map;
        wrapper._map = map;
        nativeMarker.setMap(map._native);
        return wrapper;
      },
      on: function (name, handler) {
        if (name !== 'click') return wrapper;
        clickHandler = handler;
        if (!clickListener) {
          clickListener = function (event) {
            event.preventDefault();
            event.stopPropagation();
            kakao.maps.event.preventMap();
            if (clickHandler) clickHandler(event);
          };
          content.addEventListener('click', clickListener);
        }
        return wrapper;
      },
      setLatLng: function (next) { nativeMarker.setPosition(latLng(next)); return wrapper; },
      setIcon: function (next) { applyIcon(next); return wrapper; },
      setZIndexOffset: function (value) { nativeMarker.setZIndex(Number(value) || 0); return wrapper; },
      remove: function () {
        nativeMarker.setMap(null);
        currentMap = null;
        wrapper._map = null;
      }
    };
    return wrapper;
  }

  function circle(position, options) {
    options = options || {};
    const nativeCircle = new kakao.maps.Circle({
      center: latLng(position),
      radius: options.radius || 300,
      strokeColor: options.color,
      strokeOpacity: options.opacity,
      strokeWeight: options.weight,
      fillColor: options.fillColor,
      fillOpacity: options.fillOpacity
    });
    const wrapper = {
      _native: nativeCircle,
      _map: null,
      addTo: function (map) { wrapper._map = map; nativeCircle.setMap(map._native); return wrapper; },
      remove: function () { nativeCircle.setMap(null); wrapper._map = null; }
    };
    return wrapper;
  }

  function locationMarker(position, options) {
    const size = Math.max(12, Number(options.radius || 8) * 2);
    return marker(position, {
      icon: htmlIcon({
        html: '<span class="kakao-current-location" style="width:' + size + 'px;height:' + size
          + 'px;border-color:' + options.color + ';background:' + options.fillColor + '"></span>'
      }),
      title: '현재 위치'
    });
  }

  function map(element, options) {
    options = options || {};
    const minZoom = Number(options.minZoom || MIN_ZOOM);
    const maxZoom = Number(options.maxZoom || MAX_ZOOM);
    const nativeMap = new kakao.maps.Map(element, {
      center: new kakao.maps.LatLng(37.4138, 127.1792),
      level: toKakaoLevel(13)
    });
    if (typeof nativeMap.setMinLevel === 'function') nativeMap.setMinLevel(toKakaoLevel(maxZoom));
    if (typeof nativeMap.setMaxLevel === 'function') nativeMap.setMaxLevel(toKakaoLevel(minZoom));

    const wrapper = {
      _native: nativeMap,
      getZoom: function () { return fromKakaoLevel(nativeMap.getLevel()); },
      setView: function (center, zoom) {
        nativeMap.setCenter(latLng(center));
        nativeMap.setLevel(toKakaoLevel(zoom));
        return wrapper;
      },
      hasLayer: function (layer) { return Boolean(layer && layer._map === wrapper); },
      removeLayer: function (layer) {
        if (layer && layer._native) layer._native.setMap(null);
        if (layer) layer._map = null;
        return wrapper;
      },
      on: function (name, handler) {
        kakao.maps.event.addListener(nativeMap, name === 'zoomend' ? 'zoom_changed' : name, handler);
        return wrapper;
      },
      flyToBounds: function (bounds, config) {
        config = config || {};
        const topLeft = config.paddingTopLeft || [0, 0];
        const bottomRight = config.paddingBottomRight || [0, 0];
        nativeMap.setBounds(bounds.native || bounds, topLeft[1], bottomRight[0], bottomRight[1], topLeft[0]);
        if (config.maxZoom) window.setTimeout(function () {
          if (wrapper.getZoom() > config.maxZoom) nativeMap.setLevel(toKakaoLevel(config.maxZoom));
        }, 0);
        return wrapper;
      },
      invalidateSize: function () { nativeMap.relayout(); return wrapper; },
      flyTo: function (center, zoom) {
        nativeMap.setLevel(toKakaoLevel(zoom));
        nativeMap.panTo(latLng(center));
        return wrapper;
      },
      getBounds: function () {
        const bounds = nativeMap.getBounds();
        const sw = bounds.getSouthWest();
        const ne = bounds.getNorthEast();
        const result = {
          native: bounds,
          minLat: sw.getLat(), maxLat: ne.getLat(), minLng: sw.getLng(), maxLng: ne.getLng()
        };
        result.contains = function (point) {
          const value = coordinates(point);
          return value.latitude >= result.minLat && value.latitude <= result.maxLat
            && value.longitude >= result.minLng && value.longitude <= result.maxLng;
        };
        return result;
      },
      getCenter: function () { return nativeMap.getCenter(); },
      setZoomAround: function (center, zoom) {
        const currentZoom = wrapper.getZoom();
        const requestedZoom = Number(zoom);
        const nextZoom = requestedZoom > currentZoom
          ? currentZoom + 1
          : (requestedZoom < currentZoom ? currentZoom - 1 : currentZoom);
        nativeMap.setCenter(latLng(center));
        nativeMap.setLevel(toKakaoLevel(nextZoom));
        return wrapper;
      }
    };
    return wrapper;
  }

  global.L = {
    divIcon: htmlIcon,
    latLngBounds: boundsFrom,
    marker: marker,
    circle: circle,
    circleMarker: locationMarker,
    map: map,
    tileLayer: function () { return { addTo: function () { return this; } }; }
  };
})(window);
