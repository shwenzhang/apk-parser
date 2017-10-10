package tinker.net.dongliu.apk.parser.utils;

import tinker.net.dongliu.apk.parser.bean.Locales;
import tinker.net.dongliu.apk.parser.exception.ParserException;
import tinker.net.dongliu.apk.parser.parser.StringPoolEntry;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import tinker.net.dongliu.apk.parser.struct.AndroidConstants;
import tinker.net.dongliu.apk.parser.struct.ResValue;
import tinker.net.dongliu.apk.parser.struct.ResourceValue;
import tinker.net.dongliu.apk.parser.struct.StringPool;
import tinker.net.dongliu.apk.parser.struct.StringPoolHeader;
import tinker.net.dongliu.apk.parser.struct.resource.ResourceEntry;
import tinker.net.dongliu.apk.parser.struct.resource.ResourcePackage;
import tinker.net.dongliu.apk.parser.struct.resource.ResourceTable;
import tinker.net.dongliu.apk.parser.struct.resource.Type;
import tinker.net.dongliu.apk.parser.struct.resource.TypeSpec;

/**
 * @author dongliu
 */
public class ParseUtils {

    public static Charset charsetUTF8  = Charset.forName("UTF-8");
    public static Charset charsetUTF16 = Charset.forName("UTF-16LE");

    /**
     * read string from input buffer. if get EOF before read enough data, throw IOException.
     */
    public static String readString(ByteBuffer buffer, boolean utf8) {
        if (utf8) {
            //  The lengths are encoded in the same way as for the 16-bit format
            // but using 8-bit rather than 16-bit integers.
            int strLen = readLen(buffer);
            int bytesLen = readLen(buffer);
            byte[] bytes = Buffers.readBytes(buffer, bytesLen);
            String str = new String(bytes, charsetUTF8);
            // zero
            int trailling = Buffers.readUByte(buffer);
            return str;
        } else {
            // The length is encoded as either one or two 16-bit integers as per the commentRef...
            int strLen = readLen16(buffer);
            String str = Buffers.readString(buffer, strLen);
            // zero
            int trailling = Buffers.readUShort(buffer);
            return str;
        }
    }

    /**
     * read utf-16 encoding str, use zero char to end str.
     */
    public static String readStringUTF16(ByteBuffer buffer, int strLen) {
        String str = Buffers.readString(buffer, strLen);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == 0) {
                return str.substring(0, i);
            }
        }
        return str;
    }

    /**
     * read encoding len.
     * see StringPool.cpp ENCODE_LENGTH
     */
    private static int readLen(ByteBuffer buffer) {
        int len = 0;
        int i = Buffers.readUByte(buffer);
        if ((i & 0x80) != 0) {
            //read one more byte.
            len |= (i & 0x7f) << 7;
            len += Buffers.readUByte(buffer);
        } else {
            len = i;
        }
        return len;
    }

    /**
     * read encoding len.
     * see Stringpool.cpp ENCODE_LENGTH
     */
    private static int readLen16(ByteBuffer buffer) {
        int len = 0;
        int i = Buffers.readUShort(buffer);
        if ((i & 0x8000) != 0) {
            len |= (i & 0x7fff) << 15;
            len += Buffers.readUShort(buffer);
        } else {
            len = i;
        }
        return len;
    }


    /**
     * read String pool, for apk binary xml file and resource table.
     */
    public static StringPool readStringPool(ByteBuffer buffer, StringPoolHeader stringPoolHeader) {

        long beginPos = buffer.position();
        long[] offsets = new long[(int) stringPoolHeader.getStringCount()];
        // read strings offset
        if (stringPoolHeader.getStringCount() > 0) {
            for (int idx = 0; idx < stringPoolHeader.getStringCount(); idx++) {
                offsets[idx] = Buffers.readUInt(buffer);
            }
        }
        // read flag
        // the string index is sorted by the string values if true
        boolean sorted = (stringPoolHeader.getFlags() & StringPoolHeader.SORTED_FLAG) != 0;
        // string use utf-8 format if true, otherwise utf-16
        boolean utf8 = (stringPoolHeader.getFlags() & StringPoolHeader.UTF8_FLAG) != 0;

        // read strings. the head and metas have 28 bytes
        long stringPos = beginPos + stringPoolHeader.getStringsStart() - stringPoolHeader.getHeaderSize();
        buffer.position((int) stringPos);

        StringPoolEntry[] entries = new StringPoolEntry[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            entries[i] = new StringPoolEntry(i, stringPos + offsets[i]);
        }

        String lastStr = null;
        long lastOffset = -1;
        HashMap<Integer, Long> stringOffsets = new HashMap<>();
        StringPool stringPool = new StringPool((int) stringPoolHeader.getStringCount());
        for (StringPoolEntry entry : entries) {
            stringOffsets.put(entry.getIdx(), entry.getOffset());
            if (entry.getOffset() == lastOffset) {
                stringPool.set(entry.getIdx(), lastStr);
                continue;
            }

            buffer.position((int) entry.getOffset());
            lastOffset = entry.getOffset();
            String str = ParseUtils.readString(buffer, utf8);
            lastStr = str;
            stringPool.set(entry.getIdx(), str);
        }

        // read styles
        if (stringPoolHeader.getStyleCount() > 0) {
            // now we just skip it
        }
        stringPool.setUtf8(utf8);
        stringPool.setPoolOffsets(stringOffsets);
        buffer.position((int) (beginPos + stringPoolHeader.getBodySize()));

        return stringPool;
    }

    /**
     * read res value, convert from different types to string.
     */
    @Nullable
    public static ResourceValue readResValue(ByteBuffer buffer, StringPool stringPool, ResourceTable resourceTable, Locale locale) {
        ResourceValue resValue;
        int size = Buffers.readUShort(buffer);
        short res0 = Buffers.readUByte(buffer);
        short dataType = Buffers.readUByte(buffer);

        switch (dataType) {
            case ResValue.ResType.INT_DEC:
                resValue = ResourceValue.decimal(buffer.getInt());
                break;
            case ResValue.ResType.FLOAT:
                int rawValue = buffer.getInt();
                resValue = ResourceValue.floatValue(rawValue, Float.intBitsToFloat(rawValue));
                break;
            case ResValue.ResType.ATTRIBUTE:
                resValue = ResourceValue.reference(buffer.getInt(), resourceTable, locale);
                break;
            case ResValue.ResType.INT_HEX:
                resValue = ResourceValue.hexadecimal(buffer.getInt());
                break;
            case ResValue.ResType.STRING:
                int strRef = buffer.getInt();
                if (strRef >= 0) {
                    resValue = ResourceValue.string(strRef, stringPool);
                } else {
                    resValue = null;
                }
                break;
            case ResValue.ResType.REFERENCE:
                resValue = ResourceValue.reference(buffer.getInt(), resourceTable, locale);
                break;
            case ResValue.ResType.INT_BOOLEAN:
                resValue = ResourceValue.bool(buffer.getInt());
                break;
            case ResValue.ResType.NULL:
                resValue = ResourceValue.nullValue();
                break;
            case ResValue.ResType.INT_COLOR_RGB8:
            case ResValue.ResType.INT_COLOR_RGB4:
                resValue = ResourceValue.rgb(buffer.getInt(), 6);
                break;
            case ResValue.ResType.INT_COLOR_ARGB8:
            case ResValue.ResType.INT_COLOR_ARGB4:
                resValue = ResourceValue.rgb(buffer.getInt(), 8);
                break;
            case ResValue.ResType.DIMENSION:
                resValue = ResourceValue.dimension(buffer.getInt());
                break;
            case ResValue.ResType.FRACTION:
                resValue = ResourceValue.fraction(buffer.getInt());
                break;
            default:
                resValue = ResourceValue.raw(buffer.getInt(), dataType);
                break;
        }
        if (resValue != null) {
            resValue.setDataType(dataType);
            resValue.setSize(size);
        }
        return resValue;
    }

    public static void checkChunkType(int expected, int real) {
        if (expected != real) {
            throw new ParserException("Expect chunk type:" + Integer.toHexString(expected)
                    + ", but got:" + Integer.toHexString(real));
        }
    }

    /**
     * get resource value by string-format via resourceId.
     */
    public static String getResourceById(long resourceId, ResourceTable resourceTable, Locale locale, boolean fetchRealValue) {
//        An Android Resource id is a 32-bit integer. It comprises
//        an 8-bit Package id [bits 24-31]
//        an 8-bit Type id [bits 16-23]
//        a 16-bit Entry index [bits 0-15]

        // android system styles.
        if (resourceId > AndroidConstants.SYS_STYLE_ID_START && resourceId < AndroidConstants.SYS_STYLE_ID_END) {
            return "@android:style/" + ResourceTable.sysStyle.get((int) resourceId);
        }
        String str = "resourceId:0x" + Long.toHexString(resourceId);
        if (resourceTable == null) {
            return str;
        }

        short packageId = (short) (resourceId >> 24 & 0xff);
        short typeId = (short) ((resourceId >> 16) & 0xff);
        int entryIndex = (int) (resourceId & 0xffff);

        ResourcePackage resourcePackage = resourceTable.getPackage(packageId);
        if (resourcePackage == null) {
            return str;
        }
        TypeSpec typeSpec = resourcePackage.getTypeSpec(typeId);

        List<Type> types = resourcePackage.getTypes(typeId);
        if (typeSpec == null || types == null) {
            return str;
        }
        if (!typeSpec.exists(entryIndex)) {
            return str;
        }

        // read from type resource
        ResourceEntry resource = null;
        String ref = null;
        int currentLevel = -1;
        for (Type type : types) {
            ResourceEntry curResourceEntry = type.getResourceEntry(entryIndex);

            if (curResourceEntry == null) {
                continue;
            }
            ref = curResourceEntry.getKey();

            ResourceValue currentResourceValue = curResourceEntry.getValue();
            if (currentResourceValue == null) {
                continue;
            }

            // cyclic reference detect
            if (currentResourceValue instanceof ResourceValue.ReferenceResourceValue) {
                if (resourceId == ((ResourceValue.ReferenceResourceValue) currentResourceValue)
                        .getReferenceResourceId()) {
                    continue;
                }
            }

            int level = Locales.match(locale, type.getLocale());
            if (level == 2) {
                resource = curResourceEntry;
                break;
            } else if (level > currentLevel) {
                resource = curResourceEntry;
                currentLevel = level;
            }
        }
        String result;

        if (!fetchRealValue || locale == null || resource == null) {
            result = "@" + typeSpec.getName() + "/" + ref;
        } else {
            result = resource.toStringValue();
        }
        return result;
    }

    public static String getResourceNameById(long resourceId, ResourceTable resourceTable) {
//        An Android Resource id is a 32-bit integer. It comprises
//        an 8-bit Package id [bits 24-31]
//        an 8-bit Type id [bits 16-23]
//        a 16-bit Entry index [bits 0-15]

        // android system styles.
        if (resourceId > AndroidConstants.SYS_STYLE_ID_START && resourceId < AndroidConstants.SYS_STYLE_ID_END) {
            return "@android:style/" + ResourceTable.sysStyle.get((int) resourceId);
        }
        String str = "resourceId:0x" + Long.toHexString(resourceId);
        if (resourceTable == null) {
            return str;
        }

        short packageId = (short) (resourceId >> 24 & 0xff);
        short typeId = (short) ((resourceId >> 16) & 0xff);
        int entryIndex = (int) (resourceId & 0xffff);

        ResourcePackage resourcePackage = resourceTable.getPackage(packageId);
        if (resourcePackage == null) {
            return str;
        }
        TypeSpec typeSpec = resourcePackage.getTypeSpec(typeId);

        List<Type> types = resourcePackage.getTypes(typeId);
        if (typeSpec == null || types == null) {
            return str;
        }
        if (!typeSpec.exists(entryIndex)) {
            return str;
        }

        // read from type resource
        ResourceEntry resource = null;
        String ref = null;
        for (Type type : types) {
            ResourceEntry curResourceEntry = type.getResourceEntry(entryIndex);

            if (curResourceEntry == null) {
                continue;
            }
            ref = curResourceEntry.getKey();
            return ref;

        }
        String result;

        if (resource == null) {
            result = "@" + typeSpec.getName() + "/" + ref;
        } else {
            result = resource.toStringValue();
        }
        return result;
    }

}
