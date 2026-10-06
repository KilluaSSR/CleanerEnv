package killua.dev.cleaner.guard;

import android.content.pm.ProviderInfo;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
final class PublishHook implements InvocationHandler {
    private static final String MEDIA_AUTHORITY = "media";

    private final PolicyFeed feed;
    private final Object singleton;
    private final Method create;
    private volatile Object service;

    private PublishHook(PolicyFeed feed, Object singleton, Method create, Object service) {
        this.feed = feed;
        this.singleton = singleton;
        this.create = create;
        this.service = service;
    }

    static void install(PolicyFeed feed) throws ReflectiveOperationException {
        Field singletonField = Class.forName("android.app.ActivityManager")
                .getDeclaredField("IActivityManagerSingleton");
        singletonField.setAccessible(true);
        Object singleton = singletonField.get(null);

        Class<?> singletonClass = Class.forName("android.util.Singleton");
        Field instance = singletonClass.getDeclaredField("mInstance");
        instance.setAccessible(true);
        Method create = singletonClass.getDeclaredMethod("create");
        create.setAccessible(true);

        Class<?> serviceInterface = Class.forName("android.app.IActivityManager");
        synchronized (singleton) {
            PublishHook hook = new PublishHook(feed, singleton, create, instance.get(singleton));
            instance.set(singleton, Proxy.newProxyInstance(
                    serviceInterface.getClassLoader(), new Class<?>[]{serviceInterface}, hook));
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args != null && method.getName().equals("publishContentProviders")) {
            for (Object arg : args) {
                if (arg instanceof List) {
                    guardMediaProvider((List<?>) arg);
                }
            }
        }
        try {
            return method.invoke(service(), args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Object service() throws ReflectiveOperationException {
        Object current = service;
        if (current == null) {
            synchronized (this) {
                current = service;
                if (current == null) {
                    current = create.invoke(singleton);
                    service = current;
                }
            }
        }
        return current;
    }

    private void guardMediaProvider(List<?> holders) {
        for (Object holder : holders) {
            try {
                ProviderInfo info = (ProviderInfo) holder.getClass().getField("info").get(holder);
                if (info == null || !servesMedia(info.authority)) {
                    continue;
                }
                Object transport = holder.getClass().getField("provider").get(holder);
                if (transport != null) {
                    MediaGate.wrap(transport, feed);
                    Log.i(MediaGuard.TAG, "media guard: guarding provider " + info.authority);
                }
            } catch (Throwable failure) {
                Log.e(MediaGuard.TAG, "media guard: wrapping a published provider failed", failure);
            }
        }
    }

    private static boolean servesMedia(String authorities) {
        if (authorities == null) {
            return false;
        }
        for (String authority : authorities.split(";")) {
            if (authority.equals(MEDIA_AUTHORITY)) {
                return true;
            }
        }
        return false;
    }
}
