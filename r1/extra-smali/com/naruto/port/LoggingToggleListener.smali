.class public final Lcom/naruto/port/LoggingToggleListener;
.super Ljava/lang/Object;
.implements La/u0;

.field private final launcher:Lcom/naruto/port/LauncherActivity;
.field private final row:Landroid/widget/LinearLayout;

.method public constructor <init>(Lcom/naruto/port/LauncherActivity;La/v0;Landroid/widget/LinearLayout;I)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/naruto/port/LoggingToggleListener;->launcher:Lcom/naruto/port/LauncherActivity;
    iput-object p3, p0, Lcom/naruto/port/LoggingToggleListener;->row:Landroid/widget/LinearLayout;
    return-void
.end method

.method public final a(Z)V
    .locals 4
    iget-object v0, p0, Lcom/naruto/port/LoggingToggleListener;->launcher:Lcom/naruto/port/LauncherActivity;
    const-string v1, "naruto"
    const/4 v2, 0x0
    invoke-virtual {v0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
    move-result-object v1
    invoke-interface {v1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;
    move-result-object v1
    const-string v2, "logging_enabled"
    invoke-interface {v1, v2, p1}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;
    move-result-object v1
    invoke-interface {v1}, Landroid/content/SharedPreferences$Editor;->commit()Z
    move-result v1
    if-eqz v1, :save_failed
    iget-object v1, p0, Lcom/naruto/port/LoggingToggleListener;->row:Landroid/widget/LinearLayout;
    invoke-static {v1}, Lcom/naruto/port/LauncherActivity;->k(Landroid/view/View;)V
    invoke-static {v0}, Lcom/naruto/port/LoggingControl;->initialize(Landroid/content/Context;)Z
    move-result v1
    if-eqz v1, :failed
    if-eqz p1, :disabled
    const-string v1, "Logging enabled. Applies to the next game launch."
    goto :show
    :disabled
    const-string v1, "Logging disabled. Applies to the next game launch. Hardware and driver status files are retained."
    :show
    const/4 v2, 0x1
    invoke-static {v0, v1, v2}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;
    move-result-object v1
    invoke-virtual {v1}, Landroid/widget/Toast;->show()V
    return-void
    :save_failed
    const-string v1, "Could not save the logging preference. Please try again."
    goto :show
    :failed
    invoke-static {v0}, Lcom/naruto/port/LoggingControl;->warn(Landroid/content/Context;)V
    return-void
.end method
