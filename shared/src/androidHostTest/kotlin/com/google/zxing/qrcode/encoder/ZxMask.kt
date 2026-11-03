package com.google.zxing.qrcode.encoder

object ZxMask {
    fun penalty(bm: ByteMatrix) = "${MaskUtil.applyMaskPenaltyRule1(bm)}+${MaskUtil.applyMaskPenaltyRule2(bm)}+${MaskUtil.applyMaskPenaltyRule3(bm)}+${MaskUtil.applyMaskPenaltyRule4(bm)}"
}
