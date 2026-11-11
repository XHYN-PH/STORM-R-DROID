.class public final Lcom/naruto/port/LoggingControl;
.super Ljava/lang/Object;

.field private static loaded:Z

.method private static ensureLibrary()Z
    .locals 1
    sget-boolean v0, Lcom/naruto/port/LoggingControl;->loaded:Z
    if-nez v0, :loaded
    :try_start
    const-string v0, "xhynlog"
    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V
    const/4 v0, 0x1
    sput-boolean v0, Lcom/naruto/port/LoggingControl;->loaded:Z
    :try_end
    .catch Ljava/lang/LinkageError; {:try_start .. :try_end} :missing
    :loaded
    const/4 v0, 0x1
    return v0
    :missing
    move-exception v0
    const/4 v0, 0x0
    return v0
.end method

.method public static initialize(Landroid/content/Context;)Z
    .locals 3
    const-string v0, "naruto"
    const/4 v1, 0x0
    invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
    move-result-object v0
    const-string v2, "logging_enabled"
    invoke-interface {v0, v2, v1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
    move-result v0
    sput-boolean v0, Lcom/naruto/port/AppLog;->capture:Z
    invoke-static {}, Lcom/naruto/port/LoggingControl;->ensureLibrary()Z
    move-result v0
    if-eqz v0, :failed
    const-string v0, "naruto"
    const/4 v1, 0x0
    invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
    move-result-object v0
    const-string v2, "logging_enabled"
    invoke-interface {v0, v2, v1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
    move-result v0
    :try_start
    invoke-static {v0}, Lcom/naruto/port/LoggingControl;->nativeSetEnabled(Z)Z
    move-result v0
    :try_end
    .catch Ljava/lang/LinkageError; {:try_start .. :try_end} :missing
    return v0
    :missing
    move-exception v0
    :failed
    const/4 v0, 0x0
    return v0
.end method

.method public static initializeLauncher(Landroid/content/Context;)V
    .locals 1
    invoke-static {p0}, Lcom/naruto/port/LoggingControl;->initialize(Landroid/content/Context;)Z
    move-result v0
    if-nez v0, :done
    invoke-static {p0}, Lcom/naruto/port/LoggingControl;->warn(Landroid/content/Context;)V
    :done
    return-void
.end method

.method public static gameReady(Landroid/content/Context;)V
    .locals 1
    invoke-static {}, Lcom/naruto/port/LoggingControl;->ensureLibrary()Z
    move-result v0
    if-eqz v0, :failed
    :try_start
    invoke-static {}, Lcom/naruto/port/LoggingControl;->nativeInstall()Z
    move-result v0
    :try_end
    .catch Ljava/lang/LinkageError; {:try_start .. :try_end} :missing
    if-nez v0, :done
    goto :failed
    :missing
    move-exception v0
    :failed
    invoke-static {p0}, Lcom/naruto/port/LoggingControl;->warn(Landroid/content/Context;)V
    :done
    return-void
.end method

.method public static warn(Landroid/content/Context;)V
    .locals 2
    const-string v0, "Logging control is unavailable. Logging has not been fully disabled."
    const/4 v1, 0x1
    invoke-static {p0, v0, v1}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;
    move-result-object v0
    invoke-virtual {v0}, Landroid/widget/Toast;->show()V
    return-void
.end method

.method private static native nativeSetEnabled(Z)Z
.end method

.method private static native nativeInstall()Z
.end method

.method private static native nativeEarlyCutoff()Z
.end method
