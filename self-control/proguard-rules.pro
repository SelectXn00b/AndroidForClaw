# Self-Control Module ProGuard Rules

# Keep all public Skill classes and interfaces
-keep public class com.xiaomo.hermes.selfcontrol.** { *; }

# Keep all classes that implement Skill interface
-keep class * implements com.xiaomo.hermes.agent.tools.Skill { *; }

# Keep SelfControlRegistry
-keep class com.xiaomo.hermes.selfcontrol.SelfControlRegistry { *; }

# Keep all Skill execute methods (reflection may be used)
-keepclassmembers class * implements com.xiaomo.hermes.agent.tools.Skill {
    public *** execute(...);
}

# Keep SkillResult
-keep class com.xiaomo.hermes.agent.tools.SkillResult { *; }

# Keep tool definition classes
-keep class com.xiaomo.hermes.providers.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
