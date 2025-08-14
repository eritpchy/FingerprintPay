ui_print "Fingerprint Pay Batch Installer"
ls "$MODPATH"
ui_print "$ZIPFILE"
INSTALLER_MODPATH="$MODPATH"
INSTALLER_MODID="$MODID"
for ZIPFILE in $MODPATH/*-release.zip; do
    ui_print "$ZIPFILE"
    install_module
done
if [ "$KSU" ]; then
  # KernelSU Next 我不想知道为什么一定要module.prop，否则报错
  (sleep 1 && rm -rf "$INSTALLER_MODPATH/")&
  # 如果安装器不慎被安装，直接删掉
  # 直接删也是不行的，发癫
  (sleep 1 && [ -d "$NVBASE/modules/$INSTALLER_MODID/" ] && rm -rf "$NVBASE/modules/$INSTALLER_MODID/")&
  ksud module uninstall xfingerprint-pay-batch-installer&
else
  rm -rf "$INSTALLER_MODPATH/"
fi
ui_print "- Finish"
