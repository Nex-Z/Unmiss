import { IsInt, Max, Min } from 'class-validator'
import type { CategoryWeights } from '../../common/reminder-categories'

export class UpdateCategoryWeightsDto implements CategoryWeights {
  @IsInt() @Min(0) @Max(5) work!: number
  @IsInt() @Min(0) @Max(5) life!: number
  @IsInt() @Min(0) @Max(5) finance!: number
  @IsInt() @Min(0) @Max(5) health!: number
  @IsInt() @Min(0) @Max(5) social!: number
  @IsInt() @Min(0) @Max(5) entertainment!: number
  @IsInt() @Min(0) @Max(5) other!: number
}
