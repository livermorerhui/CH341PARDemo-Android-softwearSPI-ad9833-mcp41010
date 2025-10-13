package cn.wch.ch341pardemo.util;

public class FormatUtil {
    public static int hexToInt(String str){
        if (str == null || str.equals("")){
            return -1;
        }
        int result = 0;  // 存储最终结果
        int de = 1;     // 用于计算位权（十六进制的权重）
        int mlen = str.length();
        // 遍历字符串从右到左
        for (int i = mlen - 1; i >= 0; i--) {
            char iChar = str.charAt(i);  // 获取当前字符
            if (iChar >= '0' && iChar <= '9') {
                result = result + (iChar - '0') * de;
            } else if (iChar >= 'A' && iChar <= 'F') {
                result = result + (iChar - 'A' + 0x0a) * de;
            } else if (iChar >= 'a' && iChar <= 'f') {
                result = result + (iChar - 'a' + 0x0a) * de;
            } else {
                return -1;
            }
            // 增加十六进制的权重
            de *= 16;
        }
        return result;
    }

    public static String bytesToHexString(byte[] bArr) {
        if (bArr == null || bArr.length==0)
            return "";
        StringBuffer sb = new StringBuffer(bArr.length);
        String sTmp;
        for (int i = 0; i < bArr.length; i++) {
            sTmp = Integer.toHexString(0xFF & bArr[i]);
            if (sTmp.length() < 2) {
                sb.append(0);
            }
            sb.append(sTmp.toUpperCase());
            if (i != (bArr.length - 1)){
                sb.append("");
            }
        }
        return sb.toString();
    }


    public static byte[] hexStringToBytes(String hexString) {
        if (hexString==null || hexString.equals("")) {
            return new byte[0];
        }
        hexString = hexString.toLowerCase();
        final byte[] byteArray = new byte[hexString.length() >> 1];
        int index = 0;
        if (hexString.length() %2 != 0){
            return new byte[0];
        }
        for (int i = 0; i < hexString.length(); i++) {
            if (index  > hexString.length() - 1) {
                return byteArray;
            }
            byte highDit = (byte) (Character.digit(hexString.charAt(index), 16) & 0xFF);
            byte lowDit = (byte) (Character.digit(hexString.charAt(index + 1), 16) & 0xFF);
            byteArray[i] = (byte) (highDit << 4 | lowDit);
            index += 2;
        }
        return byteArray;
    }

    public static byte byteBitChange (byte value,int index,int changeValue ){
        if(index<0 || index>7){
            throw new RuntimeException();
        }
        if (changeValue != 0 && changeValue != 1){
            throw new RuntimeException();
        }
        if(changeValue == 1){
            value |= (1 << index);
        }else {
            value &= ~(1 << index);  // 将第 7 位清零
        }
        return value;

    }
}
