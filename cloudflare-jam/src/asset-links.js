// Public signing certificates for Daylight's development and production APKs.
// These fingerprints are public; private signing keys are never committed.
export const assetLinks = [{
  relation:['delegate_permission/common.handle_all_urls'],
  target:{namespace:'android_app',package_name:'com.sh1vvy.daylight.dev',sha256_cert_fingerprints:[
    '6E:67:3E:E0:DF:E3:5A:4D:C2:79:C5:0B:45:48:F1:55:0D:4F:DB:91:5E:7B:23:29:EF:34:75:33:9A:D5:1B:37'
  ]}
},{
  relation:['delegate_permission/common.handle_all_urls'],
  target:{namespace:'android_app',package_name:'com.sh1vvy.daylight',sha256_cert_fingerprints:[
    '72:08:4A:53:C4:B3:5D:42:DE:3B:28:08:9B:58:87:27:F2:6C:5E:EA:20:29:04:19:A2:63:6D:05:64:C4:47:2B'
  ]}
}];
