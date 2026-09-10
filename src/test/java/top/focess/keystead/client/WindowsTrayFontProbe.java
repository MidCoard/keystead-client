package top.focess.keystead.client;

import java.awt.Font;
import java.awt.Toolkit;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Exercises the same character conversion as the Windows native AWT menu, in a fresh JVM. */
public final class WindowsTrayFontProbe {
    public static void main(String[] labels) throws Exception {
        Font systemFont = (Font) Toolkit.getDefaultToolkit().getDesktopProperty("win.menu.font");
        Class<?> platformFont = Class.forName("sun.awt.PlatformFont");
        Constructor<?> constructor = Class.forName("sun.awt.windows.WFontPeer")
                .getDeclaredConstructor(String.class, int.class);
        constructor.setAccessible(true);
        Method convert = platformFont.getMethod("makeMultiCharsetString", String.class, boolean.class);
        for (String fontName : new String[] {Font.DIALOG, systemFont.getName()}) {
            Object peer = constructor.newInstance(fontName, systemFont.getStyle());
            for (String label : labels) {
                Object[] runs = (Object[]) convert.invoke(peer, label, false);
                if (runs == null) {
                    throw new AssertionError("Native AWT replaces characters in " + label + " with " + fontName);
                }
                StringBuilder converted = new StringBuilder();
                for (Object run : runs) {
                    Class<?> runType = run.getClass();
                    converted.append((char[]) runType.getField("charsetChars").get(run),
                            runType.getField("offset").getInt(run), runType.getField("length").getInt(run));
                }
                if (!label.contentEquals(converted)) {
                    throw new AssertionError("Native AWT changed menu text: " + converted);
                }
            }
        }
    }
}
