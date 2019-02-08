package com.mushroom.midnight.common.entity.util;

import net.minecraft.nbt.NBTTagCompound;

public class ToggleAnimation {
    private final int length;

    private int rate = 1;

    private boolean state;
    private int timer;

    public ToggleAnimation(int length) {
        this.length = length;
    }

    public void update() {
        if (this.state) {
            this.timer = Math.min(this.timer + this.rate, this.length);
        } else {
            this.timer = Math.max(this.timer - this.rate, 0);
        }
    }

    public void set(boolean state) {
        this.state = state;
    }

    public boolean get() {
        return this.state;
    }

    public void setRate(int rate) {
        this.rate = rate;
    }

    public void setTimer(int timer) {
        this.timer = timer;
    }

    public int getTimer() {
        return this.timer;
    }

    public float getScale() {
        return this.timer / (float) this.length;
    }

    public NBTTagCompound serialize(NBTTagCompound compound) {
        compound.setBoolean("state", this.state);
        compound.setShort("timer", (short) this.timer);
        compound.setByte("rate", (byte) this.rate);
        return compound;
    }

    public void deserialize(NBTTagCompound compound) {
        this.state = compound.getBoolean("state");
        this.timer = compound.getShort("timer");
        this.rate = compound.getByte("rate");
    }

    @Override
    public String toString() {
        return "ToggleAnimation{state=" + this.state + ", timer=" + this.timer + '}';
    }
}
