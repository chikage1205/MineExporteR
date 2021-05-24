package com.chikage.mineexporter;

import com.chikage.mineexporter.ctm.CTMContext;
import com.chikage.mineexporter.ctm.CTMHandler;
import com.chikage.mineexporter.utils.Range;
import de.javagl.obj.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.ArrayUtils;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.client.Minecraft.getMinecraft;

public class ExportThread extends Thread {
    private MinecraftServer server;
    private ICommandSender sender;
    private BlockPos pos1;
    private BlockPos pos2;

    private boolean isCTMSupport = true;

    public ExportThread(MinecraftServer server, ICommandSender sender, BlockPos pos1, BlockPos pos2) {
        this.sender = sender;
        this.server = server;
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public void run(){
        try {
            IResourceManager resourceManager = getMinecraft().getResourceManager();
            ResourcePackRepository rpRep = getMinecraft().getResourcePackRepository();
            BlockModelShapes bms = getMinecraft().getBlockRendererDispatcher().getBlockModelShapes();
            Obj obj = Objs.create();
            List<Mtl> mtls = new ArrayList<>();
            int faceindex = 0;

            Range range = new Range(pos1, pos2);
            int size = range.getSize();
            int processed = 0;
            boolean showed = false;

            CTMHandler ctmHandler = new CTMHandler(resourceManager, rpRep);

            for (BlockPos pos: range) {
                processed++;
                if ((processed*100/size) % 10 == 0 && (processed*100/size) % 10 != 0 && !showed) {
                    sender.sendMessage(new TextComponentString("processing " + (processed*100/size) + "%"));
                    showed = true;
                }else{
                    showed = false;
                }

                IBlockState state = sender.getEntityWorld().getBlockState(pos);

                if (state.toString().equals("minecraft:air")) continue;

                IBlockState aState = state.getActualState(sender.getEntityWorld(), pos);
                IBakedModel model = bms.getModelForState(aState);

                Vec3d offset = aState.getOffset(sender.getEntityWorld(), pos);

                double xOffset = pos.getX() - range.getMinX() + offset.x;
                double yOffset = pos.getY() - range.getMinY() + offset.y;
                double zOffset = pos.getZ() - range.getMinZ() + offset.z;

        //            VertexDataの構造覚え書き
        //            基本的に28要素のint配列で保存
        //            1頂点あたり7要素(全部で7*32bitの情報を保持)し、それが4つある
        //            以下インデックスごとの要素
        //            0...x座標(Float)
        //            1...y座標(Float)
        //            2...z座標(Float)
        //            3...頂点色(rgba)(Byte*4)
        //            4...U座標(Float)
        //            5...V座標(Float)
        //            6...法線ベクトル(Byte*3) + あまり8bit

        //            TODO TileEntityは正常に描画されない
                for (EnumFacing facing : ArrayUtils.addAll(EnumFacing.VALUES, new EnumFacing[]{null})) {
                    if (facing != null && !aState.shouldSideBeRendered(sender.getEntityWorld(), pos, facing)) continue;

                    for (BakedQuad quad : model.getQuads(aState, facing, 0)) {
                        TextureHandler texHandler = new TextureHandler(quad.getSprite());

                        int textureWidth = texHandler.getTextureWidth();
                        int textureHeight = texHandler.getTextureHeight();
                        int[] vData = quad.getVertexData();

                        for (int i = 0; i < 4; i++) {
                            int index = i * 7;

                            float x = (float) (Float.intBitsToFloat(vData[index]) + xOffset);
                            float y = (float) (Float.intBitsToFloat(vData[index + 1]) + yOffset);
                            float z = (float) (Float.intBitsToFloat(vData[index + 2]) + zOffset);

                            float u = (float) Math.round((quad.getSprite().getUnInterpolatedU((float) ((Float.intBitsToFloat(vData[index + 4]) - Float.intBitsToFloat(vData[(index+14)%21 + 4])*.001)/.999))/16.0)*textureWidth)/textureWidth;
                            float v = 1F-(float) Math.round((quad.getSprite().getUnInterpolatedV((float) ((Float.intBitsToFloat(vData[index + 5]) - Float.intBitsToFloat(vData[(index+14)%21 + 5])*.001)/.999))/16.0)*textureHeight)/textureHeight;

                            int nv = vData[index + 6];
                            float nx = (byte) ((nv) & 0xFF) / 127.0F;
                            float ny = (byte) ((nv >> 8) & 0xFF) / 127.0F;
                            float nz = (byte) ((nv >> 16) & 0xFF) / 127.0F;

                            obj.addVertex(x, y, z);
                            obj.addTexCoord(u, v);
                            obj.addNormal(nx, ny, nz);
                        }

                        String texName = texHandler.getTextureName();
                        BufferedImage texture = texHandler.getBaseTextureImage(resourceManager);

                        if(isCTMSupport) {
                            CTMContext ctx = new CTMContext(sender.getEntityWorld(), quad, pos);
                            String ctmName = texHandler.getConnectedImage(resourceManager, texture, ctmHandler, ctx);
                            if (!ctmName.equals("none")) texName += "-" + ctmName;
                        }
//                        TODO colormap実装

                        if (!mtls.stream().map(Mtl::getName).collect(Collectors.toList()).contains(texName)) {
                            String texLocation = "textures/" + texName + ".png";
                            texHandler.save(texture, Paths.get("MineExporteR/" + texLocation));

                            Mtl mtl = Mtls.create(texName);
                            mtl.setMapKd(texLocation);
                            mtls.add(mtl);
                        }

                        obj.setActiveMaterialGroupName(texName);
                        obj.addFaceWithAll(4 * faceindex, 4 * faceindex + 1, 4 * faceindex + 2, 4 * faceindex + 3);
                        faceindex += 1;
                    }
                }
            }

            File objFile = new File("MineExporteR/export.obj");
            File mtlFile = new File("MineExporteR/export.mtl");
            obj.setMtlFileNames(Collections.singletonList("export.mtl"));
            OutputStream objOutput = new FileOutputStream(objFile);
            OutputStream mtlOutput = new FileOutputStream(mtlFile);
            ObjWriter.write(obj, objOutput);
            MtlWriter.write(mtls, mtlOutput);
            mtlOutput.close();
            objOutput.close();
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "successfully exported."));
        } catch (Exception e) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "An error has occurred during export process!"));
            sender.sendMessage(new TextComponentString(TextFormatting.RED + e.getClass().getName() + ": " + e.getMessage()));
            for (StackTraceElement trace: e.getStackTrace()) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED + trace.toString()));
            }
            e.printStackTrace();
        }
    }
}
