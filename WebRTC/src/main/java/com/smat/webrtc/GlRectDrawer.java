package com.smat.webrtc;

public class GlRectDrawer extends GlGenericDrawer {
   private static final String FRAGMENT_SHADER = "void main() {\n  gl_FragColor = sample(tc);\n}\n";

   public GlRectDrawer() {
      super("void main() {\n  gl_FragColor = sample(tc);\n}\n", new ShaderCallbacks());
   }

   private static class ShaderCallbacks implements GlGenericDrawer.ShaderCallbacks {
      private ShaderCallbacks() {
      }

      public void onNewShader(GlShader shader) {
      }

      public void onPrepareShader(GlShader shader, float[] texMatrix, int frameWidth, int frameHeight, int viewportWidth, int viewportHeight) {
      }

      // $FF: synthetic method
      ShaderCallbacks(Object x0) {
         this();
      }
   }
}
