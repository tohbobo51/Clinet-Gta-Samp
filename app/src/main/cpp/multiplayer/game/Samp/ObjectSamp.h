#pragma once

#include "game/Core/Vector.h"
#include "Entity/Object/Object.h"

struct MaterialInfo
{
    uint8_t m_bCreated;
    uint16_t wModelID;
    uint32_t dwColor;

    uint32_t oldFlags;
    RwTexture* pTex;
};

enum class eObjectAttachType {
    NONE,
    TO_PLAYER,
    TO_VEHICLE,
};

class CObjectSamp
{
public:
	RwMatrix	        m_matTarget;
	RwMatrix	        m_matCurrent;
	bool 		        m_bIsMoving {false};
	float		        m_fMoveSpeed {0.f};
	bool		        m_bIsPlayerSurfing;
	bool		        m_bNeedRotate;

	CQuaternion         m_quatTarget;
	CQuaternion         m_quatStart;

	CVector             m_vecAttachedOffset;
	CVector             m_vecAttachedRotation;
	uint16_t            m_usAttachedVehicle;
    eObjectAttachType   m_bAttachedType;

	CVector 	        m_vecRot;
	CVector		        m_vecRotationTarget;
	CVector		        m_vecSubRotationTarget;
	float		        m_fDistanceToTargetPoint;
	uint32_t	        m_iStartMoveTick;
	bool 		        bNeedReAttach = false;

    RwTexture*	        m_MaterialTextTexture[MAX_MATERIALS];
    bool		        m_bHasMaterialText;

    MaterialInfo        m_pMaterials[MAX_MATERIALS];
    bool		        m_bMaterials;

    uint32_t 	        m_setTextureColor[2];
    uint8_t 	        m_setTextureAlpha[2];
    RwTexture* 	        m_cacheTextureColor[2];

	CPhysical		    *m_pEntity;
    uintptr		        m_dwGTAId;

    static inline       std::vector<CEntity*> objectToIdMap {};

	CObjectSamp(int iModel, float fPosX, float fPosY, float fPosZ, CVector vecRot, float fDrawDistance);
	~CObjectSamp();

	void Process(float fElapsedTime);
	float DistanceRemaining(RwMatrix *matPos);

	void SetPos(float x, float y, float z);
	void MoveTo(float x, float y, float z, float speed, float rX, float rY, float rZ);

	void AttachToVehicle(uint16_t usVehID, CVector* pVecOffset, CVector* pVecRot);
	void ProcessAttachToVehicle(CVehicleSamp* pVehicle);

	void InstantRotate(float x, float y, float z);
	void StopMoving();

	void GetRotation(float* pfX, float* pfY, float* pfZ);
    void SetRot(float &radX, float &radY, float &radZ);

    void SetMaterialText(int iMaterialIndex, uint8_t byteMaterialSize, const char *szFontName, uint8_t byteFontSize, uint8_t byteFontBold, uint32_t dwFontColor, uint32_t dwBackgroundColor, uint8_t byteAlign, const char *szText);

    void PaintMaterial(uint8_t ucColor1, uint8_t ucColor2);
    void SetColor(uint32_t color1, uint32_t color2);
    void SetColorAlpha(uint8_t alpha1, uint8_t alpha2);
};