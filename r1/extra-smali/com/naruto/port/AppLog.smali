.class public final Lcom/naruto/port/AppLog;
.super Ljava/lang/Object;

.field public static volatile capture:Z

.method public static d(Ljava/lang/String;Ljava/lang/String;)I
    .locals 1
    sget-boolean v0, Lcom/naruto/port/AppLog;->capture:Z
    if-eqz v0, :off
    invoke-static {p0, p1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
    return v0
    :off
    const/4 v0, 0x0
    return v0
.end method

.method public static e(Ljava/lang/String;Ljava/lang/String;)I
    .locals 1
    sget-boolean v0, Lcom/naruto/port/AppLog;->capture:Z
    if-eqz v0, :off
    invoke-static {p0, p1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
    return v0
    :off
    const/4 v0, 0x0
    return v0
.end method

.method public static e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    .locals 1
    sget-boolean v0, Lcom/naruto/port/AppLog;->capture:Z
    if-eqz v0, :off
    invoke-static {p0, p1, p2}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    move-result v0
    return v0
    :off
    const/4 v0, 0x0
    return v0
.end method

.method public static i(Ljava/lang/String;Ljava/lang/String;)I
    .locals 1
    sget-boolean v0, Lcom/naruto/port/AppLog;->capture:Z
    if-eqz v0, :off
    invoke-static {p0, p1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
    return v0
    :off
    const/4 v0, 0x0
    return v0
.end method

.method public static v(Ljava/lang/String;Ljava/lang/String;)I
    .locals 1
    sget-boolean v0, Lcom/naruto/port/AppLog;->capture:Z
    if-eqz v0, :off
    invoke-static {p0, p1}, Landroid/util/Log;->v(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
    return v0
    :off
    const/4 v0, 0x0
    return v0
.end method

.method public static w(Ljava/lang/String;Ljava/lang/String;)I
    .locals 1
    sget-boolean v0, Lcom/naruto/port/AppLog;->capture:Z
    if-eqz v0, :off
    invoke-static {p0, p1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
    return v0
    :off
    const/4 v0, 0x0
    return v0
.end method

.method public static stackTrace(Ljava/lang/Throwable;)V
    .locals 1
    sget-boolean v0, Lcom/naruto/port/AppLog;->capture:Z
    if-eqz v0, :off
    invoke-virtual {p0}, Ljava/lang/Throwable;->printStackTrace()V
    :off
    return-void
.end method

.method public static println(Ljava/io/PrintStream;Ljava/lang/String;)V
    .locals 1
    sget-boolean v0, Lcom/naruto/port/AppLog;->capture:Z
    if-eqz v0, :off
    invoke-virtual {p0, p1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V
    :off
    return-void
.end method
