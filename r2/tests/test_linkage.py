# Copyright (c) 2026 XHYN_PH. SPDX-License-Identifier: MIT
import importlib.util,tempfile,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('linkage',Path(__file__).parents[1]/'verify_linkage.py');linkage=importlib.util.module_from_spec(spec);spec.loader.exec_module(linkage)

class LinkageRegression(unittest.TestCase):
    def fixture(self,call,flags='public static'):
        temp=tempfile.TemporaryDirectory(prefix='storm-linkage-');self.addCleanup(temp.cleanup);root=Path(temp.name)
        target=root/'smali/com/naruto/port/NarutoActivity.smali';target.parent.mkdir(parents=True)
        target.write_text('.class public Lcom/naruto/port/NarutoActivity;\n.super Landroid/app/Activity;\n.method private static native nativeShaderPrepare()[I\n.end method\n.method '+flags+' r2ShaderPrepare()[I\n .locals 1\n invoke-static {}, Lcom/naruto/port/NarutoActivity;->nativeShaderPrepare()[I\n move-result-object v0\n return-object v0\n.end method\n')
        caller=root/'smali/com/naruto/r2/GameLoop.smali';caller.parent.mkdir(parents=True)
        caller.write_text('.class public Lcom/naruto/r2/GameLoop;\n.super Ljava/lang/Object;\n.method public static tick()V\n .locals 1\n invoke-static {}, Lcom/naruto/port/NarutoActivity;->'+call+'()[I\n move-result-object v0\n return-void\n.end method\n')
        return root
    def test_published_private_native_regression_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'Inaccessible APK member'):linkage.check(self.fixture('nativeShaderPrepare'))
    def test_public_bridge_is_allowed(self):self.assertEqual(linkage.check(self.fixture('r2ShaderPrepare')),1)
    def test_private_bridge_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'Inaccessible APK member'):linkage.check(self.fixture('r2ShaderPrepare','private static'))
    def test_static_invocation_mismatch_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'Static/instance mismatch'):linkage.check(self.fixture('r2ShaderPrepare','public'))
    def callback_fixture(self,callback):
        root=self.fixture('r2ShaderPrepare')
        interface=root/'smali/a/u0.smali';interface.parent.mkdir(parents=True)
        interface.write_text('.class public interface abstract La/u0;\n.super Ljava/lang/Object;\n.method public abstract a(Z)V\n.end method\n')
        listener=root/'smali/com/naruto/port/LoggingToggleListener.smali'
        listener.write_text('.class public Lcom/naruto/port/LoggingToggleListener;\n.super Ljava/lang/Object;\n.implements La/u0;\n.method public '+callback+'(Z)V\n .locals 0\n return-void\n.end method\n')
        return root
    def test_revolution_logging_callback_mismatch_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'Missing concrete interface callback La/u0;->a'):
            linkage.check(self.callback_fixture('WRONG'))
    def test_revolution_logging_callback_matches_real_interface(self):
        self.assertEqual(linkage.check(self.callback_fixture('a')),2)

if __name__=='__main__':unittest.main()
