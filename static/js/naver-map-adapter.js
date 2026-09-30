(function (global) {
  'use strict';

  function latLng(value) {
    if (value && typeof value.lat === 'function') return value;
    return new naver.maps.LatLng(Number(value[0]), Number(value[1]));
  }

  function boundsFrom(points) {
    const list = points.map(latLng);
    const lats = list.map(function (point) { return point.lat(); });
    const lngs = list.map(function (point) { return point.lng(); });
    const nativeBounds = new naver.maps.LatLngBounds(
      new naver.maps.LatLng(Math.min.apply(null, lats), Math.min.apply(null, lngs)),
      new naver.maps.LatLng(Math.max.apply(null, lats), Math.max.apply(null, lngs))
    );
    return {
      native: nativeBounds,
      contains: function (point) { return nativeBounds.hasLatLng(latLng(point)); }
    };
  }

  function htmlIcon(options) {
    return { options: options || {} };
  }

  function marker(position, options) {
    options = options || {};
    let nativeMarker;
    let currentMap = null;
    let clickHandler = null;

    function iconValue(icon) {
      if (!icon || !icon.options) return icon;
      return {
        content: icon.options.html,
        size: new naver.maps.Size(1, 1),
        anchor: new naver.maps.Point(0, 0)
      };
    }

    nativeMarker = new naver.maps.Marker({
      position: latLng(position),
      icon: iconValue(options.icon),
      title: options.title || '',
      clickable: true
    });

    const wrapper = {
      _native: nativeMarker,
      addTo: function (map) { currentMap = map; nativeMarker.setMap(map._native); return wrapper; },
      on: function (name, handler) {
        if (name === 'click') clickHandler = naver.maps.Event.addListener(nativeMarker, 'click', handler);
        return wrapper;
      },
      setLatLng: function (next) { nativeMarker.setPosition(latLng(next)); return wrapper; },
      setIcon: function (next) { nativeMarker.setIcon(iconValue(next)); return wrapper; },
      setZIndexOffset: function (value) { nativeMarker.setZIndex(value); return wrapper; },
      remove: function () {
        if (clickHandler) naver.maps.Event.removeListener(clickHandler);
        nativeMarker.setMap(null);
        currentMap = null;
      }
    };
    return wrapper;
  }

  function circle(position, options) {
    options = options || {};
    const nativeCircle = new naver.maps.Circle({
      center: latLng(position),
      radius: options.radius || 300,
      strokeColor: options.color,
      strokeOpacity: options.opacity,
      strokeWeight: options.weight,
      fillColor: options.fillColor,
      fillOpacity: options.fillOpacity,
      clickable: false
    });
    const wrapper = {
      _native: nativeCircle,
      addTo: function (map) { nativeCircle.setMap(map._native); return wrapper; },
      remove: function () { nativeCircle.setMap(null); }
    };
    return wrapper;
  }

  function locationMarker(position, options) {
    const size = Math.max(12, Number(options.radius || 8) * 2);
    return marker(position, {
      icon: htmlIcon({
        html: '<span class="naver-current-location" style="width:' + size + 'px;height:' + size + 'px;border-color:' + options.color + ';background:' + options.fillColor + '"></span>'
      }),
      title: '현재 위치'
    });
  }

  function map(element, options) {
    options = options || {};
    const nativeMap = new naver.maps.Map(element, {
      center: new naver.maps.LatLng(37.4138, 127.1792),
      zoom: 13,
      minZoom: options.minZoom || 7,
      maxZoom: options.maxZoom || 19,
      zoomControl: false,
      mapTypeControl: false,
      scaleControl: false,
      logoControl: true,
      mapDataControl: true
    });
    const wrapper = {
      _native: nativeMap,
      getZoom: function () { return nativeMap.getZoom(); },
      setView: function (center, zoom) { nativeMap.setCenter(latLng(center)); nativeMap.setZoom(Math.round(zoom)); return wrapper; },
      hasLayer: function (layer) { return Boolean(layer && layer._native && layer._native.getMap()); },
      removeLayer: function (layer) { if (layer && layer._native) layer._native.setMap(null); return wrapper; },
      on: function (name, handler) {
        naver.maps.Event.addListener(nativeMap, name === 'zoomend' ? 'zoom_changed' : name, handler);
        return wrapper;
      },
      flyToBounds: function (bounds, config) {
        nativeMap.fitBounds(bounds.native || bounds, {
          top: config.paddingTopLeft[1],
          right: config.paddingBottomRight[0],
          bottom: config.paddingBottomRight[1],
          left: config.paddingTopLeft[0]
        });
        if (config.maxZoom) setTimeout(function () {
          if (nativeMap.getZoom() > config.maxZoom) nativeMap.setZoom(Math.floor(config.maxZoom));
        }, 0);
        return wrapper;
      },
      invalidateSize: function () { naver.maps.Event.trigger(nativeMap, 'resize'); return wrapper; },
      flyTo: function (center, zoom) { nativeMap.morph(latLng(center), Math.round(zoom), { duration: 500 }); return wrapper; },
      getBounds: function () {
        const bounds = nativeMap.getBounds();
        const sw = bounds.getSW();
        const ne = bounds.getNE();
        return {
          native: bounds,
          minLat: sw.lat(), maxLat: ne.lat(), minLng: sw.lng(), maxLng: ne.lng(),
          contains: function (point) { return bounds.hasLatLng(latLng(point)); }
        };
      },
      getCenter: function () { return nativeMap.getCenter(); },
      setZoomAround: function (center, zoom) { nativeMap.setCenter(center); nativeMap.setZoom(Math.round(zoom)); return wrapper; }
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
