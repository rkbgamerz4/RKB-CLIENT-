package com.rkb.client.modules.render;

import com.rkb.client.module.Category;
import com.rkb.client.module.Module;

public class ZoomModule extends Module {

    public ZoomModule() {
        super("Zoom", Category.RENDER);
    }

    @Override
    public void onEnable() {
        System.out.println("Zoom Enabled");
    }

    @Override
    public void onDisable() {
        System.out.println("Zoom Disabled");
    }
}
