package com.github.lunatrius.schematica.client.renderer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraftforge.common.util.ForgeDirection;

import com.github.lunatrius.schematica.reference.Reference;

final class GregTechCoverRenderState implements AutoCloseable {

    private static final GregTechCoverRenderState UNCHANGED = new GregTechCoverRenderState();
    private static boolean warnedMissingTexture;

    private final Access access;
    private final Object tile;
    private final Object[] covers;
    private final Object[] originalCovers;
    private final byte originalMask;
    private int skippedMask;
    private boolean closed;

    private GregTechCoverRenderState() {
        this.access = null;
        this.tile = null;
        this.covers = null;
        this.originalCovers = null;
        this.originalMask = 0;
    }

    private GregTechCoverRenderState(Access access, Object tile) throws ReflectiveOperationException {
        this.access = access;
        this.tile = tile;
        this.covers = (Object[]) access.covers.get(tile);
        this.originalCovers = this.covers.clone();
        this.originalMask = access.validCoversMask.getByte(tile);

        boolean prepared = false;
        try {
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                if ((this.originalMask & side.flag) == 0) continue;
                try {
                    access.getCoverTexture.invoke(tile, side);
                } catch (InvocationTargetException failure) {
                    Throwable cause = failure.getCause();
                    if (!isMissingCoverTexture(cause)) {
                        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                        if (cause instanceof Error) throw (Error) cause;
                        throw failure;
                    }
                    this.skippedMask |= side.flag;
                    this.covers[side.ordinal()] = access.noCover;
                    access.validCoversMask.setByte(tile, (byte) (this.originalMask & ~this.skippedMask));
                }
            }
            prepared = true;
        } finally {
            if (!prepared) close();
        }
    }

    static GregTechCoverRenderState prepare(Object schematicTile) throws ReflectiveOperationException {
        if (schematicTile == null) return UNCHANGED;
        Access access = GregTechAccess.INSTANCE;
        if (access == null || !access.tileClass.isInstance(schematicTile) || access.validCoversMask.getByte(schematicTile) == 0) return UNCHANGED;

        GregTechCoverRenderState state = new GregTechCoverRenderState(access, schematicTile);
        if (state.skippedMask != 0 && !warnedMissingTexture) {
            warnedMissingTexture = true;
            Reference.logger.warn("Omitting unavailable GregTech cover textures in the schematic preview; saved cover data is preserved.");
        }
        return state;
    }

    private static boolean isMissingCoverTexture(Throwable failure) {
        if (!(failure instanceof NullPointerException)) return false;
        StackTraceElement[] trace = failure.getStackTrace();
        return trace.length != 0 && trace[0].getClassName().equals("gregtech.api.covers.CoverRegistry") && trace[0].getMethodName().equals("getCoverTexture");
    }

    @Override
    public void close() throws IllegalAccessException {
        if (this.skippedMask == 0 || this.closed) return;
        System.arraycopy(this.originalCovers, 0, this.covers, 0, this.originalCovers.length);
        this.access.validCoversMask.setByte(this.tile, this.originalMask);
        this.closed = true;
    }

    private static final class GregTechAccess {

        private static final Access INSTANCE = findAccess();

        private static Access findAccess() {
            try {
                ClassLoader loader = GregTechCoverRenderState.class.getClassLoader();
                Class<?> tile = Class.forName("gregtech.api.metatileentity.CoverableTileEntity", false, loader);
                Class<?> registry = Class.forName("gregtech.api.covers.CoverRegistry", true, loader);
                return new Access(tile, registry.getField("NO_COVER").get(null));
            } catch (ClassNotFoundException absent) {
                return null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                Reference.logger.warn("GregTech preview cover compatibility is unavailable for this version.", failure);
                return null;
            }
        }
    }

    private static final class Access {

        private final Class<?> tileClass;
        private final Field covers;
        private final Field validCoversMask;
        private final Method getCoverTexture;
        private final Object noCover;

        private Access(Class<?> tileClass, Object noCover) throws ReflectiveOperationException {
            this.tileClass = tileClass;
            this.covers = tileClass.getDeclaredField("covers");
            this.covers.setAccessible(true);
            this.validCoversMask = tileClass.getDeclaredField("validCoversMask");
            this.validCoversMask.setAccessible(true);
            this.getCoverTexture = tileClass.getMethod("getCoverTexture", ForgeDirection.class);
            this.noCover = noCover;
        }
    }
}
