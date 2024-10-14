package com.smat.webrtc;

enum VideoCodecType {
   VP8("video/x-vnd.on2.vp8"),
   VP9("video/x-vnd.on2.vp9"),
   H264("video/avc");

   private final String mimeType;

   private VideoCodecType(String mimeType) {
      this.mimeType = mimeType;
   }

   String mimeType() {
      return this.mimeType;
   }
}
