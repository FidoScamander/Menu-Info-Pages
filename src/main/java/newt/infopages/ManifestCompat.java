package newt.infopages;

import java.lang.reflect.Method;

final class ManifestCompat {
    private ManifestCompat() {}

    static void copyBase(Object target, Object source) {
        if (target == null || source == null) return;
        copy(target, source, "Version", "Version");
        copy(target, source, "ServerVersion", "ServerVersion");
        copy(target, source, "Authors", "Authors");
        copy(target, source, "Website", "Website");
    }

    private static void copy(Object target, Object source, String property, String sourceProperty) {
        try {
            Method getter = source.getClass().getMethod("get" + sourceProperty);
            Object value = getter.invoke(source);
            if (value == null) return;
            for (Method setter : target.getClass().getMethods()) {
                if (!setter.getName().equals("set" + property) || setter.getParameterCount() != 1) continue;
                if (setter.getParameterTypes()[0].isInstance(value)) {
                    setter.invoke(target, value);
                    return;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
