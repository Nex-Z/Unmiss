import { BadRequestException, Injectable, UnauthorizedException } from '@nestjs/common'
import { JwtService } from '@nestjs/jwt'
import { DevicesRepository, type DeviceRow } from './devices.repository'
import type { RegisterDeviceDto } from './dto/register-device.dto'
import type { UpdateAnalysisScheduleDto } from './dto/update-analysis-schedule.dto'

export interface RegisterDeviceResult {
  token: string
  device: DeviceRow
}

@Injectable()
export class DevicesService {
  constructor(
    private readonly devicesRepository: DevicesRepository,
    private readonly jwtService: JwtService,
  ) {}

  async register(dto: RegisterDeviceDto): Promise<RegisterDeviceResult> {
    const user = await this.devicesRepository.createUser()
    const device = await this.devicesRepository.createDevice({
      userId: user.id,
      name: dto.name,
      platform: dto.platform,
    })
    const token = await this.signToken(device)
    return { token, device }
  }

  async signToken(device: DeviceRow): Promise<string> {
    return this.jwtService.signAsync({
      sub: device.id,
      deviceId: device.id,
      userId: device.userId,
    })
  }

  async verifyDevice(deviceId: string): Promise<DeviceRow> {
    const device = await this.devicesRepository.findById(deviceId)
    if (!device) {
      throw new UnauthorizedException('unknown device')
    }
    return device
  }

  async updatePushToken(deviceId: string, pushToken: string): Promise<void> {
    await this.verifyDevice(deviceId)
    await this.devicesRepository.updatePushToken(deviceId, pushToken)
  }

  analysisSchedule(userId: string) {
    return this.devicesRepository.analysisSchedule(userId)
  }

  updateAnalysisSchedule(userId: string, dto: UpdateAnalysisScheduleDto) {
    try {
      new Intl.DateTimeFormat('en-US', { timeZone: dto.timezone }).format()
    } catch {
      throw new BadRequestException('invalid timezone')
    }
    return this.devicesRepository.updateAnalysisSchedule(
      userId,
      dto.times,
      dto.timezone,
    )
  }

  async deleteUserData(userId: string): Promise<void> {
    await this.devicesRepository.deleteUserData(userId)
  }
}
