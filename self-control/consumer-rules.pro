# Consumer ProGuard rules for Self-Control Module
# These rules will be automatically applied to projects that depend on this library

# Keep public API
-keep public class com.xiaomo.hermes.selfcontrol.SelfControlRegistry { *; }
-keep public class com.xiaomo.hermes.selfcontrol.*Skill { *; }
