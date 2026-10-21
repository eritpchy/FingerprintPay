package com.surcumference.fingerprint.storage;

import org.json.JSONObject;

public interface ConfigStore {
    JSONObject request(JSONObject request) throws Exception;
}
