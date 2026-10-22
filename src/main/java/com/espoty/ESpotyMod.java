package com.espoty;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ESpotyMod implements ModInitializer {
    public static final String MOD_ID = "espoty";
    public static final String MOD_NAME = "eSpoty";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] Initialized common mod components.", MOD_NAME);
    }
}
