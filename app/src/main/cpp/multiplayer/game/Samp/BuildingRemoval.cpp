#include "BuildingRemoval.h"
#include "game/Entity/Building.h"
#include "game/Entity/Dummy/Dummy.h"
#include "game/Entity/Object/Object.h"
#include "Occlusion.h"

void CBuildingRemoval::RemoveBuildingByPtr(CEntity* pEntity) {
    if (!pEntity) return;

    CVector newPos = pEntity->GetPosition();
    newPos.z -= 2000.0f;
    pEntity->SetPosn(newPos);

    pEntity->m_bRemoveFromWorld = true;

    if (pEntity->m_matrix) {
        CVector matrixPos = pEntity->m_matrix->GetPosition();
        matrixPos.z -= 2000.0f;
        pEntity->m_matrix->m_pos = matrixPos;
    }
}

bool CBuildingRemoval::IsEntityValidForRemoval(CEntity* entity) {
    if (!entity) return false;

    if (entity->m_bRemoveFromWorld || !entity->m_bIsVisible) {
        return false;
    }

    if (entity->m_nStatus & 0x80) {
        return false;
    }

    return true;
}

float CBuildingRemoval::GetDistanceBetween3DPoints(const CVector* point1, const CVector* point2) {
    if (!point1 || !point2) return FLT_MAX;

    float dx = point1->x - point2->x;
    float dy = point1->y - point2->y;
    float dz = point1->z - point2->z;

    return sqrt(dx * dx + dy * dy + dz * dz);
}

void CBuildingRemoval::RemoveOccluders(const CVector& position, float radius) {
    for (size_t i = 0; i < COcclusion::NumOccludersOnMap; i++) {
        COccluder& occluder = COcclusion::Occluders[i];

        CVector occluderPos;
        occluderPos.x = (float)occluder.m_Center.x * 0.25f;
        occluderPos.y = (float)occluder.m_Center.y * 0.25f;
        occluderPos.z = (float)occluder.m_Center.z * 0.25f;

        if (GetDistanceBetween3DPoints(&position, &occluderPos) < radius) {
            occluder.m_Center.x = 0;
            occluder.m_Center.y = 0;
            occluder.m_Center.z = 0;

            occluder.m_Length = 0;
            occluder.m_Width = 0;
            occluder.m_Height = 0;

            occluder.m_Rot.x = 0;
            occluder.m_Rot.y = 0;
            occluder.m_Rot.z = 0;
        }
    }
}

void CBuildingRemoval::ResetPoolsMatrix() {
    for (int i = 0; i < CPools::ms_pBuildingPool->m_nSize; i++) {
        CBuilding* building = CPools::ms_pBuildingPool->GetAt(i);
        if (building) {
            building->m_matrix = nullptr;
        }
    }

    for (int i = 0; i < CPools::ms_pDummyPool->m_nSize; i++) {
        CDummy* dummy = CPools::ms_pDummyPool->GetAt(i);
        if (dummy) {
            dummy->m_matrix = nullptr;
        }
    }

    for (int i = 0; i < CPools::ms_pObjectPool->m_nSize; i++) {
        CObject* object = CPools::ms_pObjectPool->GetAt(i);
        if (object) {
            object->m_matrix = nullptr;
        }
    }
}

void CBuildingRemoval::ProcessRemoveBuilding(uint32_t modelId, const CVector& pos, float radius) {
    RemoveOccluders(pos, 500.0f);

    for (int i = 0; i < CPools::ms_pBuildingPool->m_nSize; i++) {
        CBuilding* building = CPools::ms_pBuildingPool->GetAt(i);
        if (!IsEntityValidForRemoval(building)) continue;

        if (building->m_nModelIndex == modelId || modelId == -1) {
            float distance = GetDistanceBetween3DPoints(&pos, &building->GetPosition());
            if (distance <= radius) {
                RemoveBuildingByPtr(building);
            }
        }
    }

    for (int i = 0; i < CPools::ms_pDummyPool->m_nSize; i++) {
        CDummy* dummy = CPools::ms_pDummyPool->GetAt(i);
        if (!IsEntityValidForRemoval(dummy)) continue;

        if (dummy->m_nModelIndex == modelId || modelId == -1) {
            float distance = GetDistanceBetween3DPoints(&pos, &dummy->GetPosition());
            if (distance <= radius) {
                RemoveBuildingByPtr(dummy);
            }
        }
    }

    for (int i = 0; i < CPools::ms_pObjectPool->m_nSize; i++) {
        CObject* object = CPools::ms_pObjectPool->GetAt(i);
        if (!IsEntityValidForRemoval(object)) continue;

        if (object->m_nModelIndex == modelId || modelId == -1) {
            float distance = GetDistanceBetween3DPoints(&pos, &object->GetPosition());
            if (distance <= radius) {
                RemoveBuildingByPtr(object);
            }
        }
    }
}