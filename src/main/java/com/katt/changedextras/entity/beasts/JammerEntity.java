package com.katt.changedextras.entity.beasts;

import net.ltxprogrammer.changed.entity.TransfurMode;
import net.ltxprogrammer.changed.entity.latex.LatexType;
import net.ltxprogrammer.changed.init.ChangedLatexTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class JammerEntity extends AbstractWhiteCatEntity {
    public static final String VIP_TAG = "changedextras.jammer_vip";
    private static final EntityDataAccessor<Boolean> VIP =
            SynchedEntityData.defineId(JammerEntity.class, EntityDataSerializers.BOOLEAN);

    public JammerEntity(EntityType<? extends JammerEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(VIP, false);
    }

    public boolean isVip() {
        return this.entityData.get(VIP);
    }

    public void setVip(boolean vip) {
        this.entityData.set(VIP, vip);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(VIP_TAG, isVip());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVip(tag.getBoolean(VIP_TAG));
    }

    @Override
    public LatexType getLatexType() {
        return ChangedLatexTypes.DARK_LATEX.get();
    }

    @Override
    public TransfurMode getTransfurMode() {
        return TransfurMode.REPLICATION;
    }
}
