import axios from "axios";
import authHeader from "./auth-header";

const API_URL = "http://localhost:8085/api/location/";

class LocationService {
  getLocations() {
    return axios.get(API_URL + "all", { headers: authHeader() });
  }
}

export default new LocationService();