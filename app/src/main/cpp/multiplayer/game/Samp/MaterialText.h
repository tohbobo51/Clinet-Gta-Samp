//
// Created by Traw-GG on 05.10.2025.
//
#include "gui/gui.h"

#define OBJECT_MATERIAL_TEXT_ALIGN_LEFT		0
#define OBJECT_MATERIAL_TEXT_ALIGN_CENTER	1
#define OBJECT_MATERIAL_TEXT_ALIGN_RIGHT	2

class CMaterialText
{
public:
    static RwTexture* Generate(uint8_t matSize, const char* fontname, float fontSize, uint8_t bold, uint32_t fontcol, uint32_t backcol, uint8_t align, const char* szText);
    static RwTexture* GenerateNumberPlate(const char* szPlate) { return Generate(70, "", 40, 0, 0xffff6759, 0x0, OBJECT_MATERIAL_TEXT_ALIGN_CENTER, szPlate); };

    static RwTexture* PaintTexture(RwTexture* pTexture, uint32_t uiColor);
};
