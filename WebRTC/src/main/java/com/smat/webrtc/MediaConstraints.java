package com.smat.webrtc;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MediaConstraints {
   public final List<KeyValuePair> mandatory = new ArrayList();
   public final List<KeyValuePair> optional = new ArrayList();

   private static String stringifyKeyValuePairList(List<KeyValuePair> list) {
      StringBuilder builder = new StringBuilder("[");

      KeyValuePair pair;
      for(Iterator var2 = list.iterator(); var2.hasNext(); builder.append(pair.toString())) {
         pair = (KeyValuePair)var2.next();
         if (builder.length() > 1) {
            builder.append(", ");
         }
      }

      return builder.append("]").toString();
   }

   public String toString() {
      return "mandatory: " + stringifyKeyValuePairList(this.mandatory) + ", optional: " + stringifyKeyValuePairList(this.optional);
   }

   @CalledByNative
   List<KeyValuePair> getMandatory() {
      return this.mandatory;
   }

   @CalledByNative
   List<KeyValuePair> getOptional() {
      return this.optional;
   }

   public static class KeyValuePair {
      private final String key;
      private final String value;

      public KeyValuePair(String key, String value) {
         this.key = key;
         this.value = value;
      }

      @CalledByNative("KeyValuePair")
      public String getKey() {
         return this.key;
      }

      @CalledByNative("KeyValuePair")
      public String getValue() {
         return this.value;
      }

      public String toString() {
         return this.key + ": " + this.value;
      }

      public boolean equals(@Nullable Object other) {
         if (this == other) {
            return true;
         } else if (other != null && this.getClass() == other.getClass()) {
            KeyValuePair that = (KeyValuePair)other;
            return this.key.equals(that.key) && this.value.equals(that.value);
         } else {
            return false;
         }
      }

      public int hashCode() {
         return this.key.hashCode() + this.value.hashCode();
      }
   }
}
