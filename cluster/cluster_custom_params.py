import json
import os
import time

try:
    from openpilot.common.params import Params as RealParams
except ImportError:
    RealParams = None

CUSTOM_SETTINGS_PATH = os.path.join(os.path.dirname(__file__), '../../../../../py/hud_custom_settings.json')

class CustomHudParams:
    def __init__(self):
        self._real_params = RealParams() if RealParams else None
        
        # Ensure default file exists
        if not os.path.exists(CUSTOM_SETTINGS_PATH):
            os.makedirs(os.path.dirname(CUSTOM_SETTINGS_PATH), exist_ok=True)
            with open(CUSTOM_SETTINGS_PATH, 'w') as f:
                json.dump({
                    "ClusterHudTheme": "0",
                    "ClusterHudBrightness": "0",
                    "ClusterHudMirror": "0",
                    "ClusterHudScreenMode": "0",
                    "ClusterHudCameraViewMode": "0",
                    "ClusterHudRadarInfo": "4",
                    "ClusterHudRadarDisplay": "0",
                    "ClusterHudRadarSourceColor": "0",
                    "ClusterHudEncoder": "0",
                    "ClusterHudLiveFps": "0",
                    "ClusterHudCoreMode": "0",
                    "ClusterHudDebug": "0"
                }, f)
                
        self._last_read = 0
        self._cache = {}

    def _read_custom(self):
        try:
            with open(CUSTOM_SETTINGS_PATH, 'r') as f:
                self._cache = json.load(f)
        except Exception:
            pass

    def get(self, key, encoding=None):
        if key == "ClusterHud":
            if self._real_params:
                return self._real_params.get(key, encoding)
            return None
            
        # For all other settings, use custom file
        self._read_custom()
        val = self._cache.get(key)
        if val is None:
            return None
        if encoding is not None:
            if isinstance(val, str):
                return val.encode(encoding)
            return val
        return str(val) if not isinstance(val, bytes) else val

    def get_bool(self, key):
        if key == "ClusterHud":
            if self._real_params:
                return self._real_params.get_bool(key)
            return False
            
        val = self.get(key)
        if val is None:
            return False
        if isinstance(val, str):
            return val == "1"
        return bool(val)

    def get_int(self, key):
        if key == "ClusterHud":
            if self._real_params:
                return self._real_params.get_int(key)
            return 0
            
        val = self.get(key)
        if val is None:
            return 0
        try:
            return int(val)
        except ValueError:
            return 0
