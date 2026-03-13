package javax.xml.bind;

import java.util.Base64;

public final class DatatypeConverter {

    private DatatypeConverter() {
    }

    public static byte[] parseBase64Binary(String lexicalXSDBase64Binary) {
        if (lexicalXSDBase64Binary == null) {
            return null;
        }
        String s = lexicalXSDBase64Binary.trim();
        if (s.isEmpty()) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return Base64.getUrlDecoder().decode(s);
        }
    }

    public static String printBase64Binary(byte[] val) {
        if (val == null) {
            return null;
        }
        if (val.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(val);
    }
}
