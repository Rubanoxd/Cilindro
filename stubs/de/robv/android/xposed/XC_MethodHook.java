package de.robv.android.xposed;
public abstract class XC_MethodHook {
    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public Object getResult() { return null; }
        public void setResult(Object r) {}
    }
    // OJO: el tipo de retorno forma parte de la firma. Si esta clase no existe
    // con este nombre exacto, findAndHookMethod da NoSuchMethodError en el movil.
    public class Unhook {
        public void unhook() {}
    }
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}
