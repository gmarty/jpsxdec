/*
 * jPSXdec: PlayStation 1 Media Decoder/Converter in Java
 * Copyright (C) 2014-2020  Michael Sabin
 * All rights reserved.
 *
 * Redistribution and use of the jPSXdec code or any derivative works are
 * permitted provided that the following conditions are met:
 *
 *  * Redistributions may not be sold, nor may they be used in commercial
 *    or revenue-generating business activities.
 *
 *  * Redistributions that are modified from the original source must
 *    include the complete source code, including the source code for all
 *    components used by a binary built from the modified sources. However, as
 *    a special exception, the source code distributed need not include
 *    anything that is normally distributed (in either source or binary form)
 *    with the major components (compiler, kernel, and so on) of the operating
 *    system on which the executable runs, unless that component itself
 *    accompanies the executable.
 *
 *  * Redistributions must reproduce the above copyright notice, this list
 *    of conditions and the following disclaimer in the documentation and/or
 *    other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package jpsxdec.modules.ac3;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jpsxdec.cdreaders.CdFileSectorReader;
import jpsxdec.discitems.DiscItem;
import jpsxdec.discitems.SerializedDiscItem;
import jpsxdec.i18n.exception.LocalizedDeserializationFail;
import jpsxdec.i18n.exception.LoggedFailure;
import jpsxdec.i18n.log.DebugLogger;
import jpsxdec.i18n.log.ILocalizedLogger;
import jpsxdec.modules.IIdentifiedSector;
import jpsxdec.modules.SectorClaimSystem;
import jpsxdec.modules.video.Dimensions;
import jpsxdec.modules.video.IDemuxedFrame;
import jpsxdec.modules.video.ISectorClaimToDemuxedFrame;
import jpsxdec.modules.video.framenumber.FrameNumber;
import jpsxdec.modules.video.framenumber.HeaderFrameNumber;
import jpsxdec.modules.video.framenumber.IFrameNumberFormatterWithHeader;
import jpsxdec.modules.video.framenumber.IndexSectorFrameNumber;
import jpsxdec.modules.video.sectorbased.DiscItemSectorBasedVideoStream;
import jpsxdec.modules.video.sectorbased.SectorBasedVideoInfo;
import jpsxdec.modules.xa.DiscItemXaAudioStream;

public class DiscItemAceCombat3VideoStream extends DiscItemSectorBasedVideoStream {

    public static final String TYPE_ID = "AC3Vid";

    @Nonnull
    private final HeaderFrameNumber.Format _headerFrameNumberFormat;

    private static final String MAX_INV_FRAME_KEY = "Max inv frame";
    private final int _iMaxInvFrame;

    private static final String CHANNEL_KEY = "Channel";
    private final int _iChannel;

    public DiscItemAceCombat3VideoStream(@Nonnull CdFileSectorReader cd,
                                         int iStartSector, int iEndSector,
                                         @Nonnull Dimensions dim,
                                         @Nonnull IndexSectorFrameNumber.Format sectorIndexFrameNumberFormat,
                                         @Nonnull SectorBasedVideoInfo strVidInfo,
                                         @Nonnull HeaderFrameNumber.Format headerFrameNumberFormat,
                                         int iMaxInvFrame, int iChannel)
    {
        super(cd, iStartSector, iEndSector, dim, sectorIndexFrameNumberFormat, strVidInfo);
        _headerFrameNumberFormat = headerFrameNumberFormat;
        _iMaxInvFrame = iMaxInvFrame;
        _iChannel = iChannel;
    }

    public DiscItemAceCombat3VideoStream(@Nonnull CdFileSectorReader cd,
                                         @Nonnull SerializedDiscItem fields)
            throws LocalizedDeserializationFail
    {
        super(cd, fields);
        _headerFrameNumberFormat = new HeaderFrameNumber.Format(fields);
        _iMaxInvFrame = fields.getInt(MAX_INV_FRAME_KEY);
        _iChannel = fields.getInt(CHANNEL_KEY);
    }

    @Override
    public @Nonnull SerializedDiscItem serialize() {
        SerializedDiscItem serial = super.serialize();
        _headerFrameNumberFormat.serialize(serial);
        serial.addNumber(MAX_INV_FRAME_KEY, _iMaxInvFrame);
        serial.addNumber(CHANNEL_KEY, _iChannel);
        return serial;
    }

    @Override
    public @Nonnull String getSerializationTypeId() {
        return TYPE_ID;
    }

    @Override
    public boolean hasIndependentBitstream() {
        return true;
    }

    @Override
    public int getParentRating(@Nonnull DiscItem child) {
        if (!(child instanceof DiscItemXaAudioStream))
            return 0;
        if (((DiscItemXaAudioStream)child).getChannel() != _iChannel)
            return 0;

        int iOverlapPercent = child.getOverlap(this)*100 / child.getSectorLength();
        if (iOverlapPercent > 0)
            iOverlapPercent += 100;
        return iOverlapPercent;
    }

    private static final int AUDIO_SPLIT_THRESHOLD = 32;

    @Override
    public int findAudioSplitPoint(@Nonnull DiscItemXaAudioStream audio) {
        if (audio.getChannel() != _iChannel)
            return -1;

        int iStartSector = getStartSector();
        // if audio crosses the start sector
        if (audio.getStartSector() < iStartSector - AUDIO_SPLIT_THRESHOLD &&
            audio.getEndSector()  >= iStartSector)
        {
            return iStartSector - 1;
        } else {
            return -1;
        }
    }

    @Override
    public @Nonnull FrameNumber getStartFrame() {
        return _headerFrameNumberFormat.getStartFrame(_indexSectorFrameNumberFormat);
    }

    @Override
    public FrameNumber getEndFrame() {
        return _headerFrameNumberFormat.getEndFrame(_indexSectorFrameNumberFormat);
    }

    @Override
    public @Nonnull List<FrameNumber.Type> getFrameNumberTypes() {
        return Arrays.asList(FrameNumber.Type.Index, FrameNumber.Type.Header, FrameNumber.Type.Sector);
    }

    @Override
    public void fpsDump(@Nonnull PrintStream ps) throws CdFileSectorReader.CdReadException {
        SectorClaimSystem it = createClaimSystem();
        for (int iSector = 0; it.hasNext(); iSector++) {
            IIdentifiedSector isect = it.next(DebugLogger.Log).getClaimer();
            if (isect instanceof SectorAceCombat3Video) {
                SectorAceCombat3Video vidSect = (SectorAceCombat3Video) isect;
                ps.println(String.format("%-5d %-4d %d/%d",
                                         iSector,
                                         _iMaxInvFrame - vidSect.getInvertedFrameNumber(),
                                         vidSect.getChunkNumber(),
                                         vidSect.getChunksInFrame() ));
            } else {
                ps.println(String.format(
                        "%-5d X",
                        iSector));
            }

        }
    }

    @Override
    public @Nonnull ISectorClaimToDemuxedFrame makeDemuxer() {
        return new Demuxer(_iMaxInvFrame,
                           _headerFrameNumberFormat.makeFormatter(_indexSectorFrameNumberFormat),
                           _iChannel, getStartSector(), getEndSector());
    }


    /** Public facing (external) demuxer for Ace Combat 3.
     * Wraps {@link Ac3Demuxer} and sets the {@link FrameNumber} for completed 
     * frames before passing them onto the {@link IDemuxedFrame.Listener}. */
    public static class Demuxer implements ISectorClaimToDemuxedFrame, SectorAc3VideoToDemuxedAc3Frame.Listener {

        private final SectorAc3VideoToDemuxedAc3Frame _sv2f;
        private final int _iEndFrameNumber;
        @Nonnull
        private final IFrameNumberFormatterWithHeader _frameNumberFormatter;
        private final int _iStartSector, _iEndSectorInclusive;
        @CheckForNull
        private IDemuxedFrame.Listener _listener;

        public Demuxer(int iEndFrameNumber,
                       @Nonnull IFrameNumberFormatterWithHeader frameNumberFormatter,
                       int iChannel, int iStartSector, int iEndSectorInclusive)
        {
            _sv2f = new SectorAc3VideoToDemuxedAc3Frame(iChannel, this);
            _frameNumberFormatter = frameNumberFormatter;
            _iEndFrameNumber = iEndFrameNumber;
            _iStartSector = iStartSector;
            _iEndSectorInclusive = iEndSectorInclusive;
        }

        public void attachToSectorClaimer(@Nonnull SectorClaimSystem scs) {
            SectorClaimToSectorAc3Video s2ac3s = scs.getClaimer(SectorClaimToSectorAc3Video.class);
            s2ac3s.setListener(_sv2f);
            s2ac3s.setRangeLimit(_iStartSector, _iEndSectorInclusive);
        }

        public void setFrameListener(@Nonnull IDemuxedFrame.Listener listener) {
            _listener = listener;
        }

        public void frameComplete(@Nonnull DemuxedAc3Frame frame, @Nonnull ILocalizedLogger log) throws LoggedFailure {
            FrameNumber fn = _frameNumberFormatter.next(frame.getStartSector(),
                                                        _iEndFrameNumber - frame.getInvertedHeaderFrameNumber(),
                                                        log);
            frame.setFrame(fn);
            if (_listener != null)
                _listener.frameComplete(frame);
        }

    }

}
