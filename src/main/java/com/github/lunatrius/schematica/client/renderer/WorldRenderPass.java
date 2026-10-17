package com.github.lunatrius.schematica.client.renderer;

import net.minecraftforge.client.ForgeHooksClient;

class WorldRenderPass implements AutoCloseable {

    private final int previousPass;

    WorldRenderPass(int pass) {
        this.previousPass = ForgeHooksClient.getWorldRenderPass();
        ForgeHooksClient.worldRenderPass = pass;
    }

    @Override
    public void close() {
        ForgeHooksClient.worldRenderPass = this.previousPass;
    }
}
