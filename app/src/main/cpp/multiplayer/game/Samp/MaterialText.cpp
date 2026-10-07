//
// Created by Traw-GG on 05.10.2025.
//
#include "../main.h"

#include "game.h"
#include "MaterialText.h"

#include "RW/RenderWare.h"

#include "util/TextRasterizer/TextRasterizer.h"

#include <vector>
#include <string>

/* imgui_impl_renderware.h */
bool ImGui_ImplRenderWare_NewFrame();
void ImGui_ImplRenderWare_RenderDrawData(ImDrawData* draw_data);
bool ImGui_ImplRenderWare_CreateDeviceObjects();

RwTexture* CMaterialText::Generate(uint8_t matSize, const char* fontname, float fontSize, uint8_t bold, uint32_t fontcol, uint32_t backcol, uint8_t align, const char* szText)
{
    DLOG("TextRasterizer: Generating text '%s'", szText);
	
	uint8_t ucPosition = (matSize < 10 ? matSize : matSize / 10) - 1;
	if (ucPosition >= 14) {
		Log("TextRasterizer: Position '%i' is invalid, going to set 0", ucPosition);
		ucPosition = 0;
	}

	const uint16_t usMaterialSizeTable[14][2] = {
		{32, 32},
		{64, 32},
		{64, 64},
		{128, 32},
		{128, 64},
		{128, 128},
		{256, 32},
		{256, 64},
		{256, 128},
		{256, 256},
		{512, 64},
		{512, 128},
		{512, 256},
		{512, 512}
	};

    auto textRasterizer = std::make_shared<CTextRasterizer>(usMaterialSizeTable[ucPosition][0], usMaterialSizeTable[ucPosition][1]);

	CColor colBackground{ CColor::eColorEndianness::COLOR_ENDIAN_ARGB, backcol };
	textRasterizer->FillBitmapWithColor(colBackground);

	if (fontSize > 0) {
		bool bInvalidFont = true;
		
		/*std::vector<std::string> vecExcludeList{
			"Verdana",
			"Segoe UI",
			"Engravers MT"
		};
		
		for(auto const& sExcludeFont : vecExcludeList) 
		{
			if(strcicmp(sExcludeFont.c_str(), fontname) == 0) 
			{
				Log("TextRasterizer: Font '%s' is not supported, using 'Arial.ttf'", fontname);
				bInvalidFont = true;
			}
		}*/
		
		char path[260];
		if (bInvalidFont) sprintf(path, "%sSAMP/fonts/arial.ttf", g_pszStorage);
		else sprintf(path, "%sSAMP/fonts/%s.ttf", g_pszStorage, fontname);
		
		textRasterizer->SetupFont(path, fontSize);
		if (textRasterizer->IsFontReady()) {
			switch (align) {
				case 0: {
                    textRasterizer->SetTextAlignment(CTextRasterizer::eTextAlignment::TextAlignmentLeft);
					break;
				}
				case 1: {
                    textRasterizer->SetTextAlignment(CTextRasterizer::eTextAlignment::TextAlignmentCenter);
					break;
				}
				case 2: {
                    textRasterizer->SetTextAlignment(CTextRasterizer::eTextAlignment::TextAlignmentRight);
					break;
				}
				default: {
					return nullptr;
				}
			}

			CColor colFont{ CColor::eColorEndianness::COLOR_ENDIAN_ARGB, fontcol };
			CColouredTextMultiLine colouredText(colFont, szText, false);
            textRasterizer->DrawText(colouredText);
            textRasterizer->FreeFont();
		}
		else Log("TextRasterizer: Font is not ready");
	}
	else Log("TextRasterizer: Font is skipped");
	
	uint8_t* pBitmap = textRasterizer->GetBitmap();

	RwImage* pRwImage = RwImageCreate(usMaterialSizeTable[ucPosition][0], usMaterialSizeTable[ucPosition][1], 4 * 8);
	if (!pRwImage) {
		Log("TextRasterizer: Image is not created");
		return nullptr;
	}

	RwImageAllocatePixels(pRwImage);
	for (int y = 0; y < usMaterialSizeTable[ucPosition][1]; y++) {
		memcpy(pRwImage->cpPixels + pRwImage->stride * y, pBitmap + usMaterialSizeTable[ucPosition][0] * 4 * y, usMaterialSizeTable[ucPosition][0] * 4);
	}

	int iRasterWidth = 0;
	int iRasterHeight = 0;
	int iRasterDepth = 0;
	int iRasterFlags = 0;
	RwImageFindRasterFormat(
		pRwImage,
		rwRASTERTYPETEXTURE,

		&iRasterWidth,
		&iRasterHeight,
		&iRasterDepth,
		&iRasterFlags
	);

	RwRaster* pRwRaster = RwRasterCreate(iRasterWidth, iRasterHeight, iRasterDepth, iRasterFlags);
	if (!pRwRaster) {
		Log("TextRasterizer: Raster is not created");
		return nullptr;
	}

	RwRasterSetFromImage(pRwRaster, pRwImage);
	RwImageDestroy(pRwImage);
	
	RwTexture* pRwTexture = RwTextureCreate(pRwRaster);
	++pRwTexture->refCount;

    DLOG("TextRasterizer: Texture generated %08X", pRwTexture);
	return pRwTexture;
}

RwTexture* CMaterialText::PaintTexture(RwTexture* pTexture, uint32_t uiColor) {
    if (!pTexture) {
        Log("TextRasterizer: Texture is not provided");
        return pTexture;
    }

    RwRaster* pRaster = pTexture->raster;
    if (!pRaster) {
        Log("TextRasterizer: Texture raster is not provided");
        return pTexture;
    }

    RwInt32 nRasterFormat = pRaster->cFormat;
    if (nRasterFormat & rwRASTERFORMATPIXELFORMATMASK != rwRASTERFORMAT8888) {
        Log("TextRasterizer: Texture raster have not 32bpp format");
        return pTexture;
    }

    auto* pTextureBitmap = (unsigned int*)pRaster->cpPixels;
    std::vector<unsigned char> pGsBitmap(pRaster->width * pRaster->height);
    for (int iCurLine = 0; iCurLine < pRaster->height; ++iCurLine) {
        int iCurStridedLine = pRaster->stride * iCurLine / (4 * 8);
        for (int iCurRow = 0; iCurRow < pRaster->width; ++iCurRow) {
            CColor colPixel{ CColor::eColorEndianness::COLOR_ENDIAN_RGBA, pTextureBitmap[iCurStridedLine + iCurRow] };
            if (colPixel.GetAlpha() > 0) {
                pGsBitmap[iCurLine * pRaster->width + iCurRow] = std::max(0.299f * colPixel.GetRed() + 0.587f * colPixel.GetGreen() + 0.114f * colPixel.GetBlue(), 1.0f);
            }
            else {
                pGsBitmap[iCurLine * pRaster->width + iCurRow] = 0;
            }
        }
    }

    auto textRasterizer = std::make_shared<CTextRasterizer>(pRaster->width, pRaster->height);

    CColor colBackground{ 0xFF, 0xFF, 0xFF, 0x00 };
    textRasterizer->FillBitmapWithColor(colBackground);

    CColor colRaster{ CColor::eColorEndianness::COLOR_ENDIAN_ARGB, uiColor };
    textRasterizer->DrawBitmap(pGsBitmap.data(), 0, 0, 0, 0, pRaster->width, pRaster->height, pRaster->width, pRaster->width, colRaster);

    uint8_t* pBitmap = textRasterizer->GetBitmap();

    RwImage* pOutImage = RwImageCreate(pRaster->width, pRaster->height, 4 * 8);
    if (!pOutImage) {
        Log("TextRasterizer: Image is not created");
        return pTexture;
    }

    RwImageAllocatePixels(pOutImage);
    for (int y = 0; y < pRaster->height; y++) {
        memcpy(pOutImage->cpPixels + pOutImage->stride * y, pBitmap + pRaster->width * 4 * y, pRaster->width * 4);
    }

    int iRasterWidth = 0;
    int iRasterHeight = 0;
    int iRasterDepth = 0;
    int iRasterFlags = 0;
    RwImageFindRasterFormat(
            pOutImage,
            rwRASTERTYPETEXTURE,

            &iRasterWidth,
            &iRasterHeight,
            &iRasterDepth,
            &iRasterFlags
    );

    RwRaster* pOutRaster = RwRasterCreate(iRasterWidth, iRasterHeight, iRasterDepth, iRasterFlags);
    if (!pOutRaster) {
        Log("TextRasterizer: Raster is not created");
        return pTexture;
    }

    RwRasterSetFromImage(pOutRaster, pOutImage);
    RwImageDestroy(pOutImage);

    RwTexture* pOutTexture = RwTextureCreate(pOutRaster);
    ++pOutTexture->refCount;

    DLOG("TextRasterizer: Painted texture generated %08X", pOutTexture);
    return pOutTexture;
}