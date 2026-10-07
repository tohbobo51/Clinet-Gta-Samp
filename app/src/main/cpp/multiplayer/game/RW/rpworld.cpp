//
// // Created on by NoobCooder 11.04.2023.
//

#include "RenderWare.h"
#include "game/common.h"
#include "util/patch.h"

RpClump* RpClumpForAllAtomics(RpClump* clump, RpAtomicCallBack callback, void* data) {
    return CHook::CallFunction<RpClump*>(g_libGTASA + (VER_x32 ? 0x00213D66 + 1 : 0x2BA020), clump, callback, data);
}

RpGeometry* RpGeometryForAllMaterials(RpGeometry* geometry, RpMaterialCallBack fpCallBack, void* data) {
    return CHook::CallFunction<RpGeometry*>(g_libGTASA + (VER_x32 ? 0x00215F30 + 1 : 0x2BCE78), geometry, fpCallBack, data);
}

RwBool RpClumpDestroy(RpClump* clump) {
    return CHook::CallFunction<RwBool>(g_libGTASA + (VER_x32 ? 0x0021458C + 1 : 0x2BAAB0), clump);
}

RpClump* RpClumpRender(RpClump* clump) {
    return CHook::CallFunction<RpClump*>("_Z13RpClumpRenderP7RpClump", clump);
}

RpLight* RpLightCreate(RwInt32 type) {
    return CHook::CallFunction<RpLight*>(g_libGTASA + (VER_x32 ? 0x00216DB0 + 1 : 0x2BE078), type);
}

RwBool RpLightDestroy(RpLight* light) {
    return CHook::CallFunction<RwBool>(g_libGTASA + (VER_x32 ? 0x216EF4 + 1 : 0x2BE210), light);
}

RpWorld* RpWorldCreate(RwBBox* boundingBox) {
    return CHook::CallFunction<RpWorld*>(g_libGTASA + (VER_x32 ? 0x0021D144 + 1 : 0x2C6714), boundingBox);
}

RpWorld* RpWorldAddCamera(RpWorld* world, RwCamera* camera) {
    return CHook::CallFunction<RpWorld*>(g_libGTASA + (VER_x32 ? 0x0021DF84 + 1 : 0x2C78F0), world, camera);
}

RpLight* RpLightSetColor(RpLight* light, const RwRGBAReal* color) {
    return CHook::CallFunction<RpLight*>("_Z15RpLightSetColorP7RpLightPK10RwRGBAReal", light, color);
}

RpAtomic* AtomicDefaultRenderCallBack(RpAtomic* atomic) {
    return CHook::CallFunction<RpAtomic*>("_Z27AtomicDefaultRenderCallBackP8RpAtomic", atomic);
}

RpWorld* RpWorldAddLight(RpWorld* world, RpLight* light) {
    return CHook::CallFunction<RpWorld*>(g_libGTASA + (VER_x32 ? 0x0021E7B0 + 1 : 0x2C8588), world, light);
}

RpWorld* RpWorldRemoveLight(RpWorld* world, RpLight* light) {
    return CHook::CallFunction<RpWorld*>(g_libGTASA + (VER_x32 ? 0x0021E7F4 + 1 : 0x2C85F4), world, light);
}

RwBool RpAtomicDestroy(RpAtomic* atomic) {
    return CHook::CallFunction<RwBool>(g_libGTASA + (VER_x32 ? 0x0021416C + 1 : 0x2BA534), atomic);
}

RwTexture* RwTexDictionaryRemoveTexture(RwTexture* texture) {
    return CHook::CallFunction<RwTexture*>("_Z28RwTexDictionaryRemoveTextureP9RwTexture", texture);
}

void RpClumpGtaCancelStream() {
    CHook::CallFunction<void>(g_libGTASA + (VER_x32 ? 0x5D0BA8 + 1 : 0x6F4E38));
}

RwTexDictionary* RwTexDictionaryForAllTextures(RwTexDictionary* dict, RwTextureCallBack fpCallBack, void *pData) {
    CHook::CallFunction<RwTexDictionary*>("_Z29RwTexDictionaryForAllTexturesPK15RwTexDictionaryPFP9RwTextureS3_PvES4_", dict, fpCallBack, pData);
}

RwBool RwTexDictionaryDestroy(RwTexDictionary* dict) {
    return CHook::CallFunction<RwBool>("_Z22RwTexDictionaryDestroyP15RwTexDictionary", dict);
}

RpMaterial* RpMaterialSetTexture(RpMaterial* material, RwTexture* texture) {
    return CHook::CallFunction<RpMaterial*>("_Z20RpMaterialSetTextureP10RpMaterialP9RwTexture", material, texture);
}

RpClump* RpClumpRemoveAtomic(RpClump* clump, RpAtomic* atomic) {
    return CHook::CallFunction<RpClump*>("_Z19RpClumpRemoveAtomicP7RpClumpP8RpAtomic", clump, atomic);
}

RpAtomic* RpAtomicSetFrame(RpAtomic* atomic, RwFrame* frame) {
    return CHook::CallFunction<RpAtomic*>("_Z16RpAtomicSetFrameP8RpAtomicP7RwFrame", atomic, frame);
}

RwBool RpAtomicInstance(RpAtomic* atomic) {
    return CHook::CallFunction<RwBool>("_Z16RpAtomicInstanceP8RpAtomic", atomic);
}

RwFrame* RwFrameTransform(RwFrame *frame, const RwMatrix *transform, RwOpCombineType combineOp) {
    return CHook::CallFunction<RwFrame*>("_Z16RwFrameTransformP7RwFramePK11RwMatrixTag15RwOpCombineType", frame, transform, combineOp);
}

RpClump* RpClumpStreamRead(RwStream* stream) {
    return CHook::CallFunction<RpClump*>("_Z17RpClumpStreamReadP8RwStream", stream);
}

RpAtomic* RpAtomicClone(RpAtomic* atomic) {
    return CHook::CallFunction<RpAtomic*>("_Z13RpAtomicCloneP8RpAtomic", atomic);
}