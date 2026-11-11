#!/usr/bin/env python3
"""配置工具的写入边界测试：错设备、旧配置、重建的工作空间都不能被修改。"""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace

spec = importlib.util.spec_from_file_location("work_profile", Path(__file__).with_name("moto-work-profile.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
control_spec = importlib.util.spec_from_file_location("moto_control", Path(__file__).with_name("moto-work-profile-control.py"))
control = importlib.util.module_from_spec(control_spec)
control_spec.loader.exec_module(control)
ABSENT = dict(exists=False, value=None)
HIDDEN = dict(exists=True, value="1")
SHOWN = dict(exists=True, value="0")
DEVICE = dict(serial="test-device", build="test-build")
WORK = dict(id=12, serial=42, managed=True, type="android.os.usertype.profile.MANAGED", parent=0)
PLAN = dict(format=1, kind="navigation-visibility-plan", device=DEVICE, work_user=WORK, personal_user=0,
            namespace="secure", key="test_hide_nav_bar", before=ABSENT, after=HIDDEN,
            source_shown=SHOWN, source_hidden=HIDDEN)


class FakeDevice:
    def __init__(self):
        self.ident = copy.deepcopy(DEVICE)
        self.profiles = {"12": copy.deepcopy(WORK)}
        self.values = {0: copy.deepcopy(HIDDEN), 12: copy.deepcopy(ABSENT)}
        self.writes = []
        self.backup = None

    def identity(self): return self.ident
    def users(self): return self.profiles
    def read(self, user, namespace, key): return self.values[user]

    def write(self, user, namespace, key, value):
        # 写设备之前，恢复记录必须已完整落盘。
        assert self.backup.exists() and module.load(self.backup)["before"] == ABSENT
        self.writes.append((user, namespace, key, copy.deepcopy(value)))
        self.values[user] = copy.deepcopy(value)


class WorkProfileTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.backup = Path(self.directory.name) / "backup.json"
        self.device = FakeDevice()
        self.device.backup = self.backup

    def test_apply_and_restore_preserve_personal_user_and_absent_key(self):
        module.apply_plan(self.device, copy.deepcopy(PLAN), self.backup)
        self.assertEqual(self.device.values[12], HIDDEN)
        module.restore(self.device, module.load(self.backup))
        self.assertEqual(self.device.values[12], ABSENT)
        self.assertEqual(self.device.values[0], HIDDEN)
        self.assertEqual([item[0] for item in self.device.writes], [12, 12])

    def test_wrong_device_is_read_only(self):
        self.device.ident["serial"] = "another-device"
        with self.assertRaisesRegex(ValueError, "设备"):
            module.apply_plan(self.device, PLAN, self.backup)
        self.assertFalse(self.device.writes)
        self.assertFalse(self.backup.exists())

    def test_recreated_profile_is_not_identified_by_user_id_alone(self):
        self.device.profiles["12"]["serial"] = 43
        with self.assertRaisesRegex(ValueError, "工作空间"):
            module.apply_plan(self.device, PLAN, self.backup)
        self.assertFalse(self.device.writes)

    def test_changed_setting_is_never_overwritten_by_stale_plan(self):
        self.device.values[12] = SHOWN
        with self.assertRaisesRegex(ValueError, "配置已变化"):
            module.apply_plan(self.device, PLAN, self.backup)
        self.assertFalse(self.device.writes)

    def test_existing_backup_cannot_be_overwritten(self):
        self.backup.write_text("keep me")
        with self.assertRaises(FileExistsError):
            module.apply_plan(self.device, PLAN, self.backup)
        self.assertEqual(self.backup.read_text(), "keep me")
        self.assertFalse(self.device.writes)

    def test_restore_refuses_later_user_changes(self):
        module.apply_plan(self.device, PLAN, self.backup)
        self.device.values[12] = SHOWN
        with self.assertRaisesRegex(ValueError, "其他操作"):
            module.restore(self.device, PLAN)
        self.assertEqual(len(self.device.writes), 1)

    def test_global_and_personal_settings_are_rejected(self):
        for change in (dict(namespace="global"), dict(key="navigation_mode"), dict(key="hidden_api_policy"),
                       dict(personal_user=12)):
            with self.subTest(change=change), self.assertRaises(ValueError):
                module.apply_plan(self.device, {**PLAN, **change}, self.backup)
        self.assertFalse(self.device.writes)

    def test_missing_setting_is_deleted_not_written_as_null(self):
        commands = []
        device = object.__new__(module.Device)
        device.shell = lambda *args: commands.append(args)
        device.read = lambda *args: ABSENT
        device.write(12, "secure", "test_hide_nav_bar", ABSENT)
        self.assertEqual(commands, [("settings", "--user", 12, "delete", "secure", "test_hide_nav_bar")])


class MotoControlTest(unittest.TestCase):
    def setUp(self):
        self.identity = dict(serial="test-device", manufacturer="motorola", model="XT2301-5", sdk="36", build="test-build")
        self.users = {"12": dict(id=12, serial=12, parent=0, managed=True, debugging_blocked=True)}
        self.plan = dict(format=1, kind="moto-hide-overlay-plan", overlay=module.HIDE_OVERLAY,
                         device=self.identity.copy(), work_user=self.users["12"].copy(), personal_user=0, before=False, after=True)

    def test_same_user_id_after_profile_recreation_is_rejected(self):
        control.validate_target(self.identity, self.users, 0)
        self.users["12"]["serial"] = 13
        with self.assertRaisesRegex(ValueError, "工作空间身份"):
            control.validate_target(self.identity, self.users, 0)
        with self.assertRaisesRegex(ValueError, "失效"):
            control.validate_backup(self.plan, self.identity, self.users)

    def test_other_phone_and_other_firmware_are_rejected(self):
        for changed in (dict(serial="another-phone"), dict(build="updated-build")):
            with self.subTest(changed=changed), self.assertRaisesRegex(ValueError, "其他手机或系统版本"):
                control.validate_backup(self.plan, {**self.identity, **changed}, self.users)

    def test_arbitrary_overlay_or_personal_target_is_rejected(self):
        for changed in (dict(overlay="com.android.internal.systemui.navbar.threebutton"), dict(personal_user=12)):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                control.validate_backup({**self.plan, **changed}, self.identity, self.users)

    def test_instrumentation_exit_zero_does_not_mean_action_succeeded(self):
        control.require_runner_success("INSTRUMENTATION_CODE: -1")
        for output in ("INSTRUMENTATION_CODE: 0", "INSTRUMENTATION_RESULT: error=Denied\nINSTRUMENTATION_CODE: -1"):
            with self.subTest(output=output), self.assertRaises(ValueError):
                control.require_runner_success(output)

    def test_temporary_test_package_removed_when_system_rejects_action(self):
        calls = []
        device = SimpleNamespace(adb="fake-adb", serial="test-device")
        device.shell = lambda *args: ("package:/data/app/test/base.apk" if args[0] == "pm"
                                      else "INSTRUMENTATION_RESULT: error=Denied\nINSTRUMENTATION_CODE: 0")
        def fake_run(device, args, timeout=90):
            calls.append(args)
            return "Overlay reference delivered: true" if len(args) == 2 else "Success"
        with patch.object(control, "apk_paths", return_value=(Path("helper.apk"), Path("test.apk"))), \
             patch.object(control.subprocess, "run", return_value=SimpleNamespace(returncode=0, stdout="package:/data/app/helper.apk", stderr="")), \
             patch.object(control, "run", side_effect=fake_run), self.assertRaisesRegex(ValueError, "未完成"):
            control.execute_overlay_action(device, "hide-work-pill")
        self.assertEqual(calls[-1], ["shell", "pm", "uninstall", "--user", "0", control.TEST_PACKAGE])

    def test_ordinary_overlay_command_stays_blocked_by_admin_policy(self):
        device = SimpleNamespace(identity=lambda: self.identity, users=lambda: self.users)
        with self.assertRaisesRegex(ValueError, "no_debugging_features"):
            module.validate_overlay_plan(device, self.plan)


if __name__ == "__main__":
    unittest.main()
