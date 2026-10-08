// Public signing certificate for the development APK built on sh1vvy's Mac.
// Add the production package and its release certificate before distributing
// a signed production build. These fingerprints are public, never private keys.
export const assetLinks = [{
  relation:['delegate_permission/common.handle_all_urls'],
  target:{namespace:'android_app',package_name:'com.sh1vvy.daylight.dev',sha256_cert_fingerprints:[
    '6E:67:3E:E0:DF:E3:5A:4D:C2:79:C5:0B:45:48:F1:55:0D:4F:DB:91:5E:7B:23:29:EF:34:75:33:9A:D5:1B:37'
  ]}
}];
