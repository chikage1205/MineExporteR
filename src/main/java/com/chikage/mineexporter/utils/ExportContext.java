package com.chikage.mineexporter.utils;

import com.chikage.mineexporter.Main;
import com.chikage.mineexporter.ctm.CTMHandler;
import de.javagl.obj.Mtl;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.world.IBlockAccess;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExportContext {
    public final IResourceManager rm;
    public final ResourcePackRepository rpr;
    public final BlockModelShapes bms;

    public final CTMHandler ctmHandler;

    public final IBlockAccess worldIn;

    public final Range range;

    public final Map<Texture, Set<float[][][]>> faces;

    private long processedBlocks = 0;

    public ExportContext(
            IResourceManager rm,
            ResourcePackRepository rpr,
            BlockModelShapes bms,

            IBlockAccess worldIn,

            Range range,

            Map<Texture, Set<float[][][]>> faces
            ) {

        this.rm = rm;
        this.rpr = rpr;
        this.bms = bms;

        Main.logger.info("creating ctm cache...");
        this.ctmHandler = new CTMHandler(rpr);
        Main.logger.info("successfully created ctm cache.");

        this.worldIn = worldIn;

        this.range = range;

        this.faces = faces;
    }

    public synchronized void incProcessedBlocks(long amount) {
        processedBlocks += amount;
    }

    public synchronized long getProcessedBlocks() {
        return processedBlocks;
    }
}
