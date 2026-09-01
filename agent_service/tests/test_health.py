"""健康检查测试。"""

from fastapi.testclient import TestClient


def test_health_returns_service_metadata(client: TestClient) -> None:
    """健康接口应返回可供 Java 或编排平台探测的基础状态。"""

    response = client.get("/internal/ai/v1/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.json()["modelProvider"] == "mock"
