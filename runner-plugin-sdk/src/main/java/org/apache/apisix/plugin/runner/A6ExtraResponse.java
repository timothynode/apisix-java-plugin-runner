package org.apache.apisix.plugin.runner;

import java.nio.ByteBuffer;

public class A6ExtraResponse implements A6Response {
    @Override
    public ByteBuffer encode() {
        return null;
    }

    @Override
    public byte getType() {
        return 0;
    }
}
