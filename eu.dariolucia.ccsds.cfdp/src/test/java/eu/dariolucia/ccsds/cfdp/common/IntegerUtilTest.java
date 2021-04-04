package eu.dariolucia.ccsds.cfdp.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntegerUtilTest {

    @Test
    public void testDecoding() {
        // Test 0, 1 byte
        assertEquals(0L, IntegerUtil.readInteger(new byte[] { 0 }, 0, 1));
        // Test 0, 2 bytes
        assertEquals(0L, IntegerUtil.readInteger(new byte[] { 0, 0 }, 0, 2));
        // Test 0, 3 bytes
        assertEquals(0L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0}, 0, 3));
        // Test 0, 4 bytes
        assertEquals(0L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0}, 0, 4));
        // Test 0, 5 bytes
        assertEquals(0L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0}, 0, 5));
        // Test 0, 6 bytes
        assertEquals(0L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0}, 0, 6));
        // Test 0, 7 bytes
        assertEquals(0L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 0}, 0, 7));
        // Test 0, 8 bytes
        assertEquals(0L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 0, 0}, 0, 8));

        // Test 1, 1 byte
        assertEquals(1L, IntegerUtil.readInteger(new byte[] { 1 }, 0, 1));
        // Test 1, 2 bytes
        assertEquals(1L, IntegerUtil.readInteger(new byte[] { 0, 1 }, 0, 2));
        // Test 1, 3 bytes
        assertEquals(1L, IntegerUtil.readInteger(new byte[] { 0, 0 , 1}, 0, 3));
        // Test 1, 4 bytes
        assertEquals(1L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 1}, 0, 4));
        // Test 1, 5 bytes
        assertEquals(1L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 1}, 0, 5));
        // Test 1, 6 bytes
        assertEquals(1L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 1}, 0, 6));
        // Test 1, 7 bytes
        assertEquals(1L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 1}, 0, 7));
        // Test 1, 8 bytes
        assertEquals(1L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 0, 1}, 0, 8));

        // Test 21, 1 byte
        assertEquals(21L, IntegerUtil.readInteger(new byte[] { 21 }, 0, 1));
        // Test 21, 2 bytes
        assertEquals(21L, IntegerUtil.readInteger(new byte[] { 0, 21 }, 0, 2));
        // Test 21, 3 bytes
        assertEquals(21L, IntegerUtil.readInteger(new byte[] { 0, 0 , 21}, 0, 3));
        // Test 21, 4 bytes
        assertEquals(21L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 21}, 0, 4));
        // Test 21, 5 bytes
        assertEquals(21L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 21}, 0, 5));
        // Test 21, 6 bytes
        assertEquals(21L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 21}, 0, 6));
        // Test 21, 7 bytes
        assertEquals(21L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 21}, 0, 7));
        // Test 21, 8 bytes
        assertEquals(21L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 0, 21}, 0, 8));

        // Test 221, 1 byte
        assertEquals(221L, IntegerUtil.readInteger(new byte[] { (byte) 221 }, 0, 1));
        // Test 221, 2 bytes
        assertEquals(221L, IntegerUtil.readInteger(new byte[] { 0, (byte) 221 }, 0, 2));
        // Test 221, 3 bytes
        assertEquals(221L, IntegerUtil.readInteger(new byte[] { 0, 0 , (byte) 221}, 0, 3));
        // Test 221, 4 bytes
        assertEquals(221L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, (byte) 221}, 0, 4));
        // Test 221, 5 bytes
        assertEquals(221L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, (byte) 221}, 0, 5));
        // Test 221, 6 bytes
        assertEquals(221L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, (byte) 221}, 0, 6));
        // Test 221, 7 bytes
        assertEquals(221L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, (byte) 221}, 0, 7));
        // Test 221, 8 bytes
        assertEquals(221L, IntegerUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 0,(byte) 221}, 0, 8));

        // Test 0x984325, 3 bytes
        assertEquals(0x00984325L, IntegerUtil.readInteger(new byte[] { (byte) 0x98, (byte) 0x43, (byte) 0x25}, 0, 3));
        // Test 0x984325, 4 bytes
        assertEquals(0x00984325L, IntegerUtil.readInteger(new byte[] { 0, (byte) 0x98, (byte) 0x43, (byte) 0x25}, 0, 4));
        // Test 0x984325, 5 bytes
        assertEquals(0x00984325L, IntegerUtil.readInteger(new byte[] { 0, 0, (byte) 0x98, (byte) 0x43, (byte) 0x25}, 0, 5));
    }

    @Test
    public void testEncoding() {
        assertArrayEquals(new byte[] { 0 }, IntegerUtil.encodeInteger(0L, 1));
        assertArrayEquals(new byte[] { 0, 0 }, IntegerUtil.encodeInteger(0L, 2));
        assertArrayEquals(new byte[] { 0, 0, 0 }, IntegerUtil.encodeInteger(0L, 3));
        assertArrayEquals(new byte[] { 0, 0, 0, 0 }, IntegerUtil.encodeInteger(0L, 4));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0 }, IntegerUtil.encodeInteger(0L, 5));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0 }, IntegerUtil.encodeInteger(0L, 6));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0 }, IntegerUtil.encodeInteger(0L, 7));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 }, IntegerUtil.encodeInteger(0L, 8));

        assertArrayEquals(new byte[] { 1 }, IntegerUtil.encodeInteger(1L, 1));
        assertArrayEquals(new byte[] { 0, 1 }, IntegerUtil.encodeInteger(1L, 2));
        assertArrayEquals(new byte[] { 0, 0, 1 }, IntegerUtil.encodeInteger(1L, 3));
        assertArrayEquals(new byte[] { 0, 0, 0, 1 }, IntegerUtil.encodeInteger(1L, 4));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 1 }, IntegerUtil.encodeInteger(1L, 5));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 1 }, IntegerUtil.encodeInteger(1L, 6));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 1 }, IntegerUtil.encodeInteger(1L, 7));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0, 1 }, IntegerUtil.encodeInteger(1L, 8));

        assertArrayEquals(new byte[] { 21 }, IntegerUtil.encodeInteger(21L, 1));
        assertArrayEquals(new byte[] { 0, 21 }, IntegerUtil.encodeInteger(21L, 2));
        assertArrayEquals(new byte[] { 0, 0, 21 }, IntegerUtil.encodeInteger(21L, 3));
        assertArrayEquals(new byte[] { 0, 0, 0, 21 }, IntegerUtil.encodeInteger(21L, 4));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 21 }, IntegerUtil.encodeInteger(21L, 5));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 21 }, IntegerUtil.encodeInteger(21L, 6));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 21 }, IntegerUtil.encodeInteger(21L, 7));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0, 21 }, IntegerUtil.encodeInteger(21L, 8));

        assertArrayEquals(new byte[] { (byte) 0x98, (byte) 0x43, (byte) 0x25 }, IntegerUtil.encodeInteger(0x00984325L, 3));
        assertArrayEquals(new byte[] { 0, (byte) 0x98, (byte) 0x43, (byte) 0x25 }, IntegerUtil.encodeInteger(0x00984325L, 4));
        assertArrayEquals(new byte[] { 0, 0, (byte) 0x98, (byte) 0x43, (byte) 0x25 }, IntegerUtil.encodeInteger(0x00984325L, 5));
    }
}