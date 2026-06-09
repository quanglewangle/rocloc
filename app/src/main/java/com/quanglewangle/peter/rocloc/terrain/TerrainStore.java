package com.quanglewangle.peter.rocloc.terrain;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores OS Terrain 50 tiles on disk.
 * Binary format per tile: 4 bytes originE (int32 LE) + 4 bytes originN (int32 LE)
 *                         + 200*200*4 bytes float32 LE elevation values (north-first).
 */
public class TerrainStore {

    private static final int GRID_SIZE = 200 * 200;
    private static final int FILE_SIZE = 8 + GRID_SIZE * 4; // header + data

    private final File dir;
    private final Map<String, float[]> memCache = new HashMap<>();

    public TerrainStore(Context ctx) {
        dir = new File(ctx.getFilesDir(), "terrain");
        dir.mkdirs();
    }

    public boolean hasTile(String code) {
        return tileFile(code).exists();
    }

    public boolean hasTiles() {
        String[] files = dir.list();
        return files != null && files.length > 0;
    }

    public int tileCount() {
        String[] files = dir.list((d, name) -> name.endsWith(".bin"));
        return files == null ? 0 : files.length;
    }

    /** Returns the 40,000-element elevation grid, or null if not on disk. */
    public float[] getTile(String code) {
        code = code.toUpperCase();
        float[] cached = memCache.get(code);
        if (cached != null) return cached;
        File f = tileFile(code);
        if (!f.exists()) return null;
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] data = new byte[FILE_SIZE];
            int read = 0;
            while (read < FILE_SIZE) {
                int n = fis.read(data, read, FILE_SIZE - read);
                if (n < 0) break;
                read += n;
            }
            if (read < FILE_SIZE) return null;
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            buf.getInt(); // originE (not needed; computed from code)
            buf.getInt(); // originN
            float[] grid = new float[GRID_SIZE];
            buf.asFloatBuffer().get(grid);
            memCache.put(code, grid);
            return grid;
        } catch (IOException e) {
            return null;
        }
    }

    /** Saves raw bytes received from the server directly to disk. */
    public void saveTile(String code, byte[] data) throws IOException {
        if (data.length < FILE_SIZE) throw new IOException("Tile data too short");
        File f = tileFile(code.toUpperCase());
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data, 0, FILE_SIZE);
        }
        memCache.remove(code.toUpperCase());
    }

    public long totalSizeBytes() {
        long total = 0;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) total += f.length();
        return total;
    }

    private File tileFile(String code) {
        return new File(dir, code.toUpperCase() + ".bin");
    }
}
