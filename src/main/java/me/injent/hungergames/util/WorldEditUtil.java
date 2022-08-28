package me.injent.hungergames.util;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Location;
import org.bukkit.Material;

public class WorldEditUtil {

    public static void fillBlocks(Location pos1, Location pos2, Material material) {
        World world = BukkitAdapter.adapt(pos1.getWorld());
        CuboidRegion selection = new CuboidRegion(world, BlockVector3.at(pos1.getX(), pos1.getY(), pos1.getZ()), BlockVector3.at(pos2.getX(), pos2.getY(), pos2.getZ()));
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
            RandomPattern pattern = new RandomPattern();
            BlockState air = BukkitAdapter.adapt(material.createBlockData());
            pattern.add(air, 1.0);
            editSession.setBlocks(selection, pattern);
        } catch (MaxChangedBlocksException e) {
            e.printStackTrace();
        }
    }
}
