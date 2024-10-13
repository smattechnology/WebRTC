package com.smat.webrtc;

import android.media.MediaCodecInfo;
import android.support.annotation.Nullable;
import java.util.Arrays;
import org.webrtc.EglBase;
/* loaded from: input.aar:classes.jar:org/webrtc/PlatformSoftwareVideoDecoderFactory.class */
public class PlatformSoftwareVideoDecoderFactory extends MediaCodecVideoDecoderFactory {
    private static final Predicate<MediaCodecInfo> defaultAllowedPredicate = new Predicate<MediaCodecInfo>() { // from class: org.webrtc.PlatformSoftwareVideoDecoderFactory.1
        private String[] prefixWhitelist = (String[]) Arrays.copyOf(MediaCodecUtils.SOFTWARE_IMPLEMENTATION_PREFIXES, MediaCodecUtils.SOFTWARE_IMPLEMENTATION_PREFIXES.length);

        @Override // org.webrtc.Predicate
        public boolean test(MediaCodecInfo arg) {
            String[] strArr;
            String name = arg.getName();
            for (String prefix : this.prefixWhitelist) {
                if (name.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    };

    @Override // org.webrtc.MediaCodecVideoDecoderFactory, org.webrtc.VideoDecoderFactory
    public /* bridge */ /* synthetic */ VideoCodecInfo[] getSupportedCodecs() {
        return super.getSupportedCodecs();
    }

    @Override // org.webrtc.MediaCodecVideoDecoderFactory, org.webrtc.VideoDecoderFactory
    @Nullable
    public /* bridge */ /* synthetic */ VideoDecoder createDecoder(VideoCodecInfo videoCodecInfo) {
        return super.createDecoder(videoCodecInfo);
    }

    public PlatformSoftwareVideoDecoderFactory(@Nullable EglBase.Context sharedContext) {
        super(sharedContext, defaultAllowedPredicate);
    }
}
