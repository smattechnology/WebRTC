package com.smat.webrtc;

interface BitrateAdjuster {
   void setTargets(int var1, int var2);

   void reportEncodedFrame(int var1);

   int getAdjustedBitrateBps();

   int getCodecConfigFramerate();
}
