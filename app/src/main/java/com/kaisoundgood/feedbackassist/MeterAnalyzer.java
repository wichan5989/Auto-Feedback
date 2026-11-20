package com.kaisoundgood.feedbackassist;

import java.util.Locale;

/** Byte-level stream comparison only. No frequency or dB interpretation. */
public final class MeterAnalyzer {
    private byte[] first;
    private byte[] previous;
    private int packets;
    private int changedFrames;
    private int totalChangedBytes;
    private int maxChangedBytes;
    private int lengthChanges;
    private int firstLength;
    private int lastLength;
    private int prefix32;
    private int prefix32Changes;

    public void accept(byte[] data, int length) {
        if (length < 0 || length > data.length) return;
        byte[] sample = java.util.Arrays.copyOf(data,length);
        if (first == null) {
            first = sample;
            firstLength = length;
            prefix32 = littleEndianInt(sample);
        } else {
            int changed = Math.abs(length - previous.length);
            int common = Math.min(length,previous.length);
            for (int i=0;i<common;i++) if(sample[i]!=previous[i]) changed++;
            if (changed>0) changedFrames++;
            totalChangedBytes+=changed;
            maxChangedBytes=Math.max(maxChangedBytes,changed);
            if(length!=previous.length) lengthChanges++;
            if(littleEndianInt(sample)!=littleEndianInt(previous)) prefix32Changes++;
        }
        previous=sample;
        lastLength=length;
        packets++;
    }

    private static int littleEndianInt(byte[] a) {
        if(a==null || a.length<4) return -1;
        return (a[0]&255)|((a[1]&255)<<8)|((a[2]&255)<<16)|((a[3]&255)<<24);
    }

    public String report() {
        return String.format(Locale.US,
            "packets=%d,firstBytes=%d,lastBytes=%d,firstLE32=%d,prefixChanges=%d,changedFrames=%d,changedBytes=%d,maxChangedPerFrame=%d,lengthChanges=%d",
            packets,firstLength,lastLength,prefix32,prefix32Changes,changedFrames,totalChangedBytes,maxChangedBytes,lengthChanges);
    }
}
