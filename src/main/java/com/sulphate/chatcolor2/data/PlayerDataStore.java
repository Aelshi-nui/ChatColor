package com.sulphate.chatcolor2.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class PlayerDataStore {

    protected final Map<UUID, PlayerData> dataMap;

    public PlayerDataStore() {
        dataMap = new ConcurrentHashMap<>();
    }

    public PlayerData getPlayerData(UUID uuid) {
        return dataMap.get(uuid);
    }

    public abstract void loadPlayerData(UUID uuid, Callback<Boolean> callback);

    public String getColour(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        return data == null ? null : data.getColour();
    }

    public void setColour(UUID uuid, String colour) {
        PlayerData data = dataMap.get(uuid);

        if (data == null) {
            return;
        }

        data.setColour(colour);
        savePlayerData(uuid);
    }

    public long getDefaultCode(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        return data == null ? -1 : data.getDefaultCode();
    }

    public void setDefaultCode(UUID uuid, long defaultCode) {
        PlayerData data = dataMap.get(uuid);

        if (data == null) {
            return;
        }

        data.setDefaultCode(defaultCode);
        savePlayerData(uuid);
    }

    public abstract void savePlayerData(UUID uuid);

    public void saveAllData() {
         for (UUID uuid : dataMap.keySet()) {
             savePlayerData(uuid);
         }
     }

    public abstract void shutdown();

}
