package tinker.net.dongliu.apk.parser.struct;

import tinker.net.dongliu.apk.parser.struct.resource.ResourceTable;
import tinker.net.dongliu.apk.parser.utils.ParseUtils;

import java.util.Locale;

/**
 * Resource entity, may be one entry in resource table, or string value
 * A apk only has one resource table.
 *
 * @author dongliu
 */
public abstract class ResourceValue {
    protected final int value;
    protected int   size;
    protected short dataType;

    protected ResourceValue(int value) {
        this.value = value;
    }

    public int getSize() {
        return size;
    }

    public short getDataType() {
        return dataType;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void setDataType(short dataType) {
        this.dataType = dataType;
    }

    /**
     * get value as string
     * @return value
     */
    public abstract String toStringValue();

    public static ResourceValue decimal(int value) {
        return new DecimalResourceValue(value);
    }

    public static ResourceValue floatValue(int rawValue, float value) {
        return new FloatResourceValue(rawValue, value);
    }

    public static ResourceValue hexadecimal(int value) {
        return new HexadecimalResourceValue(value);
    }

    public static ResourceValue bool(int value) {
        return new BooleanResourceValue(value);
    }

    public static ResourceValue string(int value, StringPool stringPool) {
        return new StringResourceValue(value, stringPool);
    }

    public static ResourceValue reference(int value, ResourceTable resourceTable, Locale locale) {
        return new ReferenceResourceValue(value, resourceTable, locale);
    }

    public static ResourceValue nullValue() {
        return NullResourceValue.instance;
    }

    public static ResourceValue rgb(int value, int len) {
        return new RGBResourceValue(value, len);
    }

    public static ResourceValue dimension(int value) {
        return new DimensionValue(value);
    }

    public static ResourceValue fraction(int value) {
        return new FractionValue(value);
    }

    public static ResourceValue raw(int value, short type) {
        return new RawValue(value, type);
    }


    private static class DecimalResourceValue extends ResourceValue {

        private DecimalResourceValue(int value) {
            super(value);
        }

        @Override
        public String toStringValue() {
            return String.valueOf(value);
        }
    }

    private static class FloatResourceValue extends ResourceValue {
        private final float mValue;

        private FloatResourceValue(int rawValue, float value) {
            super(rawValue);
            mValue = value;
        }

        @Override
        public String toStringValue() {
            return String.valueOf(mValue);
        }
    }

    private static class HexadecimalResourceValue extends ResourceValue {

        private HexadecimalResourceValue(int value) {
            super(value);
        }

        @Override
        public String toStringValue() {
            return "0x" + Integer.toHexString(value);
        }
    }

    private static class BooleanResourceValue extends ResourceValue {

        private BooleanResourceValue(int value) {
            super(value);
        }

        @Override
        public String toStringValue() {
            return String.valueOf(value != 0);
        }
    }

    private static class StringResourceValue extends ResourceValue {
        private final StringPool stringPool;

        private StringResourceValue(int value, StringPool stringPool) {
            super(value);
            this.stringPool = stringPool;
        }

        @Override
        public String toStringValue() {
            if (value >= 0) {
                return stringPool.get(value);
            } else {
                return null;
            }
        }
    }

    // make public for cyclic reference detect
    public static class ReferenceResourceValue extends ResourceValue {
        private final ResourceTable resourceTable;
        private final Locale        locale;

        private ReferenceResourceValue(int value, ResourceTable resourceTable, Locale locale) {
            super(value);
            this.resourceTable = resourceTable;
            this.locale = locale;
        }

        @Override
        public String toString() {
            return toStringRepresent(false);
        }

        @Override
        public String toStringValue() {
            return toStringRepresent(true);
        }

        private String toStringRepresent(boolean fetchRealValue) {
            long resourceId = getReferenceResourceId();
            return ParseUtils.getResourceById(resourceId, resourceTable, locale, fetchRealValue);
        }

        public long getReferenceResourceId() {
            return value & 0xFFFFFFFFL;
        }
    }

    private static class NullResourceValue extends ResourceValue {
        private static final NullResourceValue instance = new NullResourceValue();

        private NullResourceValue() {
            super(-1);
        }

        @Override
        public String toStringValue() {
            return "";
        }
    }

    private static class RGBResourceValue extends ResourceValue {
        private final int len;

        private RGBResourceValue(int value, int len) {
            super(value);
            this.len = len;
        }

        @Override
        public String toStringValue() {
            StringBuilder sb = new StringBuilder();
            for (int i = len / 2 - 1; i >= 0; i--) {
                sb.append(Integer.toHexString((value >> i * 8) & 0xff));
            }
            return sb.toString();
        }
    }

    private static class DimensionValue extends ResourceValue {

        private DimensionValue(int value) {
            super(value);
        }

        @Override
        public String toStringValue() {
            short unit = (short) (value & 0xff);
            String unitStr;
            switch (unit) {
                case ResValue.ResDataCOMPLEX.UNIT_MM:
                    unitStr = "mm";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_PX:
                    unitStr = "px";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_DIP:
                    unitStr = "dp";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_SP:
                    unitStr = "sp";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_PT:
                    unitStr = "pt";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_IN:
                    unitStr = "in";
                    break;
                default:
                    unitStr = "unknown unit:0x" + Integer.toHexString(unit);
            }
            return (value >> 8) + unitStr;
        }
    }

    private static class FractionValue extends ResourceValue {

        private FractionValue(int value) {
            super(value);
        }

        @Override
        public String toStringValue() {
            // The low-order 4 bits of the data value specify the type of the fraction
            short type = (short) (value & 0xf);
            String pstr;
            switch (type) {
                case ResValue.ResDataCOMPLEX.UNIT_FRACTION:
                    pstr = "%";
                    break;
                case ResValue.ResDataCOMPLEX.UNIT_FRACTION_PARENT:
                    pstr = "%p";
                    break;
                default:
                    pstr = "unknown type:0x" + Integer.toHexString(type);
            }
            float f = Float.intBitsToFloat(value >> 4);
            return f + pstr;
        }
    }

    private static class RawValue extends ResourceValue {
        private final short dataType;

        private RawValue(int value, short dataType) {
            super(value);
            this.dataType = dataType;
        }

        @Override
        public String toStringValue() {
            return "{" + dataType + ":" + (value & 0xFFFFFFFFL) + "}";
        }
    }
}
