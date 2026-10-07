//
// // Created on by NoobCooder 19.04.2023.
//
#include "rwplcore.h"
#include "../../main.h"
#include "util/patch.h"

RwMatrix* RwMatrixUpdate(RwMatrix* matrix) {
    matrix->flags &= 0xFFFDFFFC;
    return matrix;
}

RwBool RwMatrixDestroy(RwMatrix* mpMat) {
    return CHook::CallFunction<RwBool>("_Z15RwMatrixDestroyP11RwMatrixTag", mpMat);
}

RwV3d* RwV3dTransformPoint(RwV3d* pointOut, const RwV3d* pointIn, const RwMatrix* matrix) {
    return CHook::CallFunction<RwV3d*>(g_libGTASA + (VER_x32 ? 0x001E690C + 1 : 0x28231C), pointOut, pointIn, matrix);
}

RwV3d* RwV3dTransformPoints(RwV3d* pointsOut, const RwV3d* pointsIn, RwInt32 numPoints, const RwMatrix* matrix) {
    return CHook::CallFunction<RwV3d*>(g_libGTASA + (VER_x32 ? 0x1E6934 + 1 : 0x28235C), pointsOut, pointsIn, numPoints, matrix);
}

RwMatrix* RwMatrixOrthoNormalize(RwMatrix* matrixOut, const RwMatrix* matrixIn) {
    return CHook::CallFunction<RwMatrix*>(g_libGTASA + (VER_x32 ? 0x001E3420 + 1 : 0x27E280), matrixOut, matrixIn);
}

RwUInt32 RwStreamRead(RwStream* stream, void* buffer, RwUInt32 length) {
    return CHook::CallFunction<RwUInt32>(g_libGTASA + (VER_x32 ? 0x001E56D4 + 1 : 0x28091C), stream, buffer, length);
}

RwStream* RwStreamOpen(RwStreamType type, RwStreamAccessType accessType, const void* data) {
    return CHook::CallFunction<RwStream*>(g_libGTASA + (VER_x32 ? 0x001E59F0 + 1 : 0x280E44), type, accessType, data);
}

RwBool RwStreamClose(RwStream* stream, void* data) {
    return CHook::CallFunction<RwBool>("_Z13RwStreamCloseP8RwStreamPv", stream, data);
}

RwMatrix* RwMatrixTransform(RwMatrix* matrix, const RwMatrix* transform, RwOpCombineType combineOp) {
    return CHook::CallFunction<RwMatrix*>("_Z17RwMatrixTransformP11RwMatrixTagPKS_15RwOpCombineType", matrix, transform, combineOp);
}

RwMatrix* RwMatrixCreate() {
    return CHook::CallFunction<RwMatrix*>("_Z14RwMatrixCreatev");
}

RwMatrix* RwMatrixRotate(RwMatrix* pMat, CVector* axis, RwReal angle) {
    return CHook::CallFunction<RwMatrix*>("_Z14RwMatrixRotateP11RwMatrixTagPK5RwV3df15RwOpCombineType", pMat, axis, angle, rwCOMBINEPRECONCAT);
}

RwMatrix* RwMatrixTranslate(RwMatrix *matrix, const RwV3d *translation, RwOpCombineType combineOp) {
    return CHook::CallFunction<RwMatrix*>("_Z17RwMatrixTranslateP11RwMatrixTagPK5RwV3d15RwOpCombineType", matrix, translation, combineOp);
}

RwV3d* RwV3dTransformVector(RwV3d* vectorOut, const RwV3d* vectorIn, const RwMatrix* matrix) {
    return CHook::CallFunction<RwV3d*>("_Z20RwV3dTransformVectorP5RwV3dPKS_PK11RwMatrixTag", vectorOut, vectorIn, matrix);
}

RwReal RwV3dNormalize(RwV3d* out, const RwV3d* in) {
    return CHook::CallFunction<RwReal>("_Z14RwV3dNormalizeP5RwV3dPKS_", out, in);
}

RwMatrix* RwMatrixMultiply(RwMatrix* matrixOut, const RwMatrix* MatrixIn1, const RwMatrix* matrixIn2) {
    return CHook::CallFunction<RwMatrix*>("_Z16RwMatrixMultiplyP11RwMatrixTagPKS_S2_", matrixOut, MatrixIn1, matrixIn2);
}

RwBool RwStreamFindChunk(RwStream* stream, RwUInt32 type, RwUInt32* lengthOut, RwUInt32* versionOut) {
    return CHook::CallFunction<RwBool>("_Z17RwStreamFindChunkP8RwStreamjPjS1_", stream, type, lengthOut, versionOut);
}